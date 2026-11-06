package com.velorise.simplemap.client;

import java.util.*;

/** Presentation only. Grouping never changes journals or individual map coordinates. */
public final class PlaceGroups {
    public static final class Group {
        private final String key;
        private final List<PlaceEntry> entries;
        private int index;
        private Group(String key,List<PlaceEntry> entries,String selected) {
            this.key=key;this.entries=List.copyOf(entries);
            for(int i=0;i<entries.size();i++)if(entries.get(i).id().equals(selected))index=i;
        }
        public String key(){return key;}
        public PlaceEntry current(){return entries.get(index);}
        public int size(){return entries.size();}
        public int index(){return index;}
        public void cycle(int step){index=Math.floorMod(index+step,entries.size());}
    }
    public static List<Group> collect(List<PlaceEntry> entries,List<Group> previous){
        Map<String,String> selected=new HashMap<>();
        for(var g:previous)selected.put(g.key(),g.current().id());
        Map<String,List<PlaceEntry>> groups=new LinkedHashMap<>();
        for(var e:entries){
            String key=e.location()!=null?"place:"+e.dimension()+":"+e.location().kind()+":"+e.title():"mark:"+e.id();
            groups.computeIfAbsent(key,k->new ArrayList<>()).add(e);
        }
        List<Group> result=new ArrayList<>();
        groups.forEach((key,list)->result.add(new Group(key,list,selected.get(key))));
        return List.copyOf(result);
    }
}
