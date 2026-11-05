package com.f8q.party;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
public class CoverageCopyProbe {
    public static void main(String[] args)throws Exception{
        try(var in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(Path.of(args[0])))))){
            if(in.readInt()!=0x46385131)throw new IOException();int users=in.readInt();
            for(int u=0;u<users;u++){
                in.readLong();in.readLong();int n=in.readInt();var set=new HashSet<Exploration.Key>();
                for(int i=0;i<n;i++)set.add(new Exploration.Key(in.readUTF(),in.readInt(),in.readInt()));
                long t=System.nanoTime();var slow=Set.copyOf(set);long immutable=System.nanoTime()-t;
                t=System.nanoTime();var fast=Exploration.snapshot(set);long hash=System.nanoTime()-t;
                if(!slow.equals(new HashSet<>(fast)))throw new AssertionError("coverage mismatch");
                System.out.printf(Locale.ROOT,"coverage=%d immutable_copy_ms=%.2f production_snapshot_ms=%.2f%n",n,immutable/1e6,hash/1e6);
                int tiles=in.readInt();for(int i=0;i<tiles;i++){in.readUTF();in.readInt();in.readInt();in.skipNBytes(1024);}
            }
        }
    }
}
