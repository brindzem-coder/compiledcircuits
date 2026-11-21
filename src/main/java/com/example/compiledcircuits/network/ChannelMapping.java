package com.example.compiledcircuits.network;

import java.math.BigInteger;
import java.util.*;

/** Bounded, immutable directed channel mapping. Repeated destinations are OR inputs. */
public record ChannelMapping(String text, List<Edge> edges) {
    public static final int MAX_TEXT=2048, MAX_CHANNELS=256, MAX_EDGES=1024;
    public record Edge(String from,String to) {}
    private record Range(BigInteger first,BigInteger last) {
        int size(){return last.subtract(first).intValueExact()+1;}
        String at(int i){return first.add(BigInteger.valueOf(i)).toString();}
        String label(){return first.equals(last)?first.toString():first+"-"+last;}
    }
    private static Range range(String text){
        var bounds=text.split("-",-1);
        if(bounds.length<1||bounds.length>2)throw new IllegalArgumentException("Expected an ID or ascending range");
        var first=new BigInteger(WireIds.normalize(bounds[0]));var last=bounds.length==1?first:new BigInteger(WireIds.normalize(bounds[1]));
        if(last.compareTo(first)<0||last.subtract(first).compareTo(BigInteger.valueOf(MAX_CHANNELS-1))>0)
            throw new IllegalArgumentException("Ranges must ascend and contain at most "+MAX_CHANNELS+" channels");
        return new Range(first,last);
    }
    public static ChannelMapping parse(String text){
        if(text==null||text.length()>MAX_TEXT)throw new IllegalArgumentException("Mapping is too long ("+MAX_TEXT+" characters)");
        // Whitespace may surround tokens, but must never join two separate numbers.
        text=text.trim().replaceAll("\\s*([,=\\-])\\s*","$1");
        var edges=new LinkedHashSet<Edge>();var clauses=new ArrayList<String>();var ids=new HashSet<String>();
        for(var clause:text.split(",",-1)){
            var sides=clause.split("=",-1);if(sides.length>2)throw new IllegalArgumentException("Only one = is allowed per rule");
            var left=range(sides[0]);var right=sides.length==1?left:range(sides[1]);
            int a=left.size(),b=right.size();
            if(a!=b&&a!=1&&b!=1)throw new IllegalArgumentException("Range lengths must match, or one side must be a single ID");
            for(int i=0;i<Math.max(a,b);i++){
                var edge=new Edge(left.at(a==1?0:i),right.at(b==1?0:i));edges.add(edge);ids.add(edge.from());ids.add(edge.to());
                if(edges.size()>MAX_EDGES||ids.size()>MAX_CHANNELS)throw new IllegalArgumentException("Mapping exceeds channel or connection limit");
            }
            clauses.add(left.label()+(sides.length==1?"":"="+right.label()));
        }
        return new ChannelMapping(String.join(",",clauses),List.copyOf(edges));
    }
    public Set<String> targets(){var set=new TreeSet<String>(WireIds::compare);for(var e:edges)set.add(e.to());return set;}
    public boolean equivalent(ChannelMapping other){return new HashSet<>(edges).equals(new HashSet<>(other.edges));}
}
