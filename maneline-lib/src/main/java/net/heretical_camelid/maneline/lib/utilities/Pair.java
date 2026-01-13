package net.heretical_camelid.maneline.lib.utilities;

public class Pair<T1,T2> implements java.lang.Comparable<Pair<T1,T2>> {
    public final T1 first;
    public final T2 second;
    public Pair(T1 _first, T2 _second) {
        first = _first;
        second = _second;
    }

    @Override
    public int compareTo(Pair<T1, T2> other) {
        int c = first.toString().compareTo(other.first.toString());
        if(c==0) {
            c = second.toString().compareTo(other.second.toString());
        }
        return c;
    }
}
