// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.kore.compile;

import org.kframework.kore.*;

import java.util.ArrayList;

import static org.kframework.kore.KORE.*;

/**
 * Created by brandon on 3/30/15.
 */
public class TransformKORE extends AbstractKORETransformer<K> {

    @Override
    public K apply(KApply k) {
        ArrayList<K> newItems = new ArrayList<>(k.klist().items());
        boolean change = false;
        for (int i = 0; i < newItems.size(); ++i) {
            K in = newItems.get(i);
            K out = apply(in);
            newItems.set(i, out);
            change = change || (in != out);
        }
        if (change) {
            return KApply(k.klabel(), KList(newItems), k.att());
        } else {
            return k;
        }
    }

    @Override
    public K apply(KRewrite k) {
        K l = apply(k.left());
        K r = apply(k.right());
        if (l != k.left() || r != k.right()) {
            return KRewrite(l, r, k.att());
        } else {
            return k;
        }
    }

    @Override
    public K apply(KToken k) {
        return k;
    }

    @Override
    public K apply(KVariable k) {
        return k;
    }

    @Override
    public K apply(KSequence k) {
        ArrayList<K> newItems = new ArrayList<>(k.items());
        boolean change = false;
        for (int i = 0; i < newItems.size(); ++i) {
            K in = newItems.get(0);
            K out = apply(newItems.get(0));
            newItems.set(i, out);
            change = change || (in != out);
        }
        if (change) {
            return KSequence(newItems, k.att());
        } else {
            return k;
        }
    }

    @Override
    public K apply(InjectedKLabel k) {
        return k;
    }
}
