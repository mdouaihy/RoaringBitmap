package org.roaringbitmap.runcontainer;


import it.uniroma3.mat.extendedset.intset.ConciseSet;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.roaringbitmap.FastAggregation;
import org.roaringbitmap.RoaringBitmap;
import org.roaringbitmap.ZipRealDataRetriever;
import org.roaringbitmap.buffer.ImmutableRoaringBitmap;
import org.roaringbitmap.buffer.MutableRoaringBitmap;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class RunContainerRealDataBenchmarkHorizontal {

    static ConciseSet toConcise(int[] dat) {
        ConciseSet ans = new ConciseSet();
        for (int i : dat) {
            ans.add(i);
        }
        return ans;
    }
    

    static ConciseSet toWAH(int[] dat) {
        ConciseSet ans = new ConciseSet(true);
        for (int i : dat) {
            ans.add(i);
        }
        return ans;
    }

    @Benchmark
    public int horizontalOr_RoaringWithRun(BenchmarkState benchmarkState) {
        int answer = FastAggregation.naive_or(benchmarkState.rc.iterator())
               .getCardinality();
        if(answer != benchmarkState.horizontalor)
            throw new RuntimeException("buggy horizontal or");
        return answer;
    }

    @Benchmark
    public int horizontalOr_Roaring(BenchmarkState benchmarkState) {
        int answer = FastAggregation.naive_or(benchmarkState.ac.iterator())
               .getCardinality();
        if(answer != benchmarkState.horizontalor)
            throw new RuntimeException("buggy horizontal or");
        return answer;
    }
    

    @Benchmark
    public int horizontalOr_Concise(BenchmarkState benchmarkState) {
        ConciseSet bitmapor = benchmarkState.cc.get(0);
        for (int j = 1; j < benchmarkState.cc.size() ; ++j) {
            bitmapor = bitmapor.union(benchmarkState.cc.get(j));
        }
        int answer = bitmapor.size();
        if(answer != benchmarkState.horizontalor)
            throw new RuntimeException("buggy horizontal or");
        return answer;
    }

    @Benchmark
    public int horizontalOr_WAG(BenchmarkState benchmarkState) {
        ConciseSet bitmapor = benchmarkState.wah.get(0);
        for (int j = 1; j < benchmarkState.wah.size() ; ++j) {
            bitmapor = bitmapor.union(benchmarkState.wah.get(j));
        }
        int answer = bitmapor.size();
        if(answer != benchmarkState.horizontalor)
            throw new RuntimeException("buggy horizontal or");
        return answer;
    }



    @State(Scope.Benchmark)
    public static class BenchmarkState {
        @Param ({// putting the data sets in alpha. order
            "census-income", "census1881",
            "dimension_008", "dimension_003", 
            "dimension_033", "uscensus2000", 
            "weather_sept_85", "wikileaks-noquotes"
                ,"census-income_srt","census1881_srt",
                "weather_sept_85_srt","wikileaks-noquotes_srt"
        })
        String dataset;

        int horizontalor = 0;

        ArrayList<RoaringBitmap> rc = new ArrayList<RoaringBitmap>();
        ArrayList<RoaringBitmap> ac = new ArrayList<RoaringBitmap>();
        ArrayList<ImmutableRoaringBitmap> mrc = new ArrayList<ImmutableRoaringBitmap>();
        ArrayList<ImmutableRoaringBitmap> mac = new ArrayList<ImmutableRoaringBitmap>();
        ArrayList<ConciseSet> cc = new ArrayList<ConciseSet>();
        ArrayList<ConciseSet> wah = new ArrayList<ConciseSet>();


        public BenchmarkState() {
        }
                
        @Setup
        public void setup() throws Exception {
            ZipRealDataRetriever dataRetriever = new ZipRealDataRetriever(dataset);
            System.out.println();
            System.out.println("Loading files from " + dataRetriever.getName());

            int normalsize = 0;
            int runsize = 0;
            int concisesize = 0;
            int wahsize = 0;
            long stupidarraysize = 0;
            long stupidbitmapsize = 0;
            int totalcount = 0;
            int numberofbitmaps = 0;
            int universesize = 0;
            final int MAX_NUMBER = 20000; // we put an upper bound on the number of bitmaps loaded to avoid pathological cases
            for (int[] data : dataRetriever.fetchBitPositions()) {
                numberofbitmaps++;
                if(universesize < data[data.length - 1 ])
                    universesize = data[data.length - 1 ];
                stupidarraysize += 8 + data.length * 4L;
                stupidbitmapsize += 8 + (data[data.length - 1] + 63L) / 64 * 8;
                totalcount += data.length;
                MutableRoaringBitmap mbasic = MutableRoaringBitmap.bitmapOf(data);
                MutableRoaringBitmap mopti = mbasic.clone();
                mopti.runOptimize();

                RoaringBitmap basic = RoaringBitmap.bitmapOf(data);
                RoaringBitmap opti = basic.clone();
                opti.runOptimize();
                ConciseSet concise = toConcise(data);
                ConciseSet w = toWAH(data);
                wah.add(w);
                wahsize += (int) (concise.size() * concise
                        .collectionCompressionRatio()) * 4;
                rc.add(opti);
                ac.add(basic);
                mrc.add(mopti);
                mac.add(mbasic);
                cc.add(concise);
                if(basic.serializedSizeInBytes() != mbasic.serializedSizeInBytes())
                    throw new RuntimeException("size mismatch");
                if(opti.serializedSizeInBytes() != mopti.serializedSizeInBytes())
                    throw new RuntimeException("size mismatch");
                normalsize += basic.serializedSizeInBytes();
                runsize += opti.serializedSizeInBytes();
                concisesize += (int) (concise.size() * concise
                                      .collectionCompressionRatio()) * 4;
                if(mrc.size() >= MAX_NUMBER) break;
            }

            /***
             * This is a hack. JMH does not allow us to report
             * anything directly ourselves, so we do it forcefully.
             */
            DecimalFormat df = new DecimalFormat("0.0");
            System.out.println();
            System.out.println("==============");
            System.out.println("= data set "+dataset);
            System.out.println("Number of bitmaps = " + numberofbitmaps
                               + " total count = " + totalcount
                               + " universe size = "+universesize);
            System.out.println("Average bits per bitmap = "
                               + df.format(totalcount * 1.0 / numberofbitmaps));
            System.out.println("Run-roaring total     = "
                    + String.format("%1$10s", "" + runsize)
                    + "B, average per bitmap = "
                    + String.format("%1$10s",df.format(runsize * 1.0 / numberofbitmaps))
                    + "B, average bits per entry =  "
                    + String.format("%1$10s",df.format(runsize * 8.0 / totalcount)));
            System.out.println("Regular roaring total = "
                    + String.format("%1$10s", "" + normalsize)
                    + "B, average per bitmap = "
                    + String.format("%1$10s",df.format(normalsize * 1.0 / numberofbitmaps))
                    + "B, average bits per entry =  "
                    + String.format("%1$10s",df.format(normalsize * 8.0 / totalcount)));
            System.out.println("Concise total         = "
                    + String.format("%1$10s", "" + concisesize)
                    + "B, average per bitmap = "
                    + String.format("%1$10s",df.format(concisesize * 1.0 / numberofbitmaps))
                    + "B, average bits per entry =  "
                    + String.format("%1$10s",df.format(concisesize * 8.0 / totalcount)));
            System.out.println("WAH total         = "
                    + String.format("%1$10s", "" + wahsize)
                    + "B, average per bitmap = "
                    + String.format("%1$10s",df.format(wahsize * 1.0 / numberofbitmaps))
                    + "B, average bits per entry =  "
                    + String.format("%1$10s",df.format(wahsize * 8.0 / totalcount)));
            System.out.println("Naive array total     = "
                    + String.format("%1$10s", "" + stupidarraysize)
                    + "B, average per bitmap = "
                    + String.format("%1$10s",df.format(stupidarraysize * 1.0 / numberofbitmaps))
                    + "B, average bits per entry =  "
                    + String.format("%1$10s",df.format(stupidarraysize * 8.0 / totalcount)));
            System.out.println("Naive bitmap total    = "
                    + String.format("%1$10s", "" + stupidbitmapsize)
                    + "B, average per bitmap = "
                    + String.format("%1$10s",df.format(stupidbitmapsize * 1.0 / numberofbitmaps))
                    + "B, average bits per entry =  "
                    + String.format("%1$10s",df.format(stupidbitmapsize * 8.0 / totalcount)));
            System.out.println("==============");
            System.out.println();
            horizontalor = FastAggregation.naive_or(rc.iterator())
                    .getCardinality();
        }

    }

}