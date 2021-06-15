package net.kunmc.lab.splitisland;

import net.querz.mca.Chunk;
import net.querz.mca.MCAFile;
import net.querz.mca.MCAUtil;
import net.querz.mca.Section;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class SplitIslandConverter {
    public static void main(String... args) {
        File mcaDirectory = new File(".");
        File[] allMca = Objects.requireNonNull(mcaDirectory.listFiles(e -> e.getName().endsWith(".mca")));

        if (allMca.length <= 0) {
            System.err.println("No .mca files found.");
            System.exit(1);
        }

        System.out.println(String.format("Conversion started! %d files", allMca.length));

        AtomicInteger doneMcaCount = new AtomicInteger();
        CompletableFuture.allOf(
                Arrays.stream(allMca)
                        .map(e -> CompletableFuture.runAsync(() -> {
                            try {
                                convertMca(e);
                            } catch (IOException ex) {
                                throw new RuntimeException("Convert Error", ex);
                            }
                        }))
                        .map(e -> e.thenRun(() -> {
                            System.out.println(String.format("Done %d/%d", doneMcaCount.incrementAndGet(), allMca.length));
                        }))
                        .toArray(CompletableFuture[]::new)
        ).join();

        System.out.println("Finished!");
    }

    public static void convertMca(File mcaFile) throws IOException {
        MCAFile mca = MCAUtil.read(mcaFile);
        IntStream.range(0, 1024).forEach(i -> {
            Chunk chunk = mca.getChunk(i);

            int x = i % 32;
            int z = i / 32;
            if (x % 2 == 0 && z % 2 == 0)
                return;

            if (chunk == null)
                return;

            IntStream.range(0, 16)
                    .mapToObj(y -> {
                        Section section = chunk.getSection(y);
                        if (section == null) {
                            section = Section.newSection();
                            chunk.setSection(y, section);
                        } else if (section.getPalette() == null) {
                            Section newSection = Section.newSection();
                            newSection.setBlockLight(section.getBlockLight());
                            newSection.setSkyLight(section.getSkyLight());
                            section = newSection;
                            chunk.setSection(y, section);
                        }
                        return section;
                    })
                    .map(Section::getPalette)
                    .forEach(palette -> {
                        palette.forEach(tag -> {
                            String name = tag.getString("Name");
                            if (!"minecraft:air".equals(name))
                                tag.putString("Name", "minecraft:air");
                        });
                    });

            mca.setChunk(i, chunk);
        });
        MCAUtil.write(mca, mcaFile);
    }
}
