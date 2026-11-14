package dev.brezze.voxymobile.core;
import dev.brezze.voxymobile.core.ingest.*;
import dev.brezze.voxymobile.core.voxel.*;
import java.nio.file.*;
import java.util.Comparator;
public final class PersistentSectionCacheSmokeTest {
 public static void main(String[] args) throws Exception {
  Path root=Files.createTempDirectory("voxymobile-cache-test");
  try {
   int[] ids=new int[VoxelSection.VOLUME]; ids[0]=42; ids[17]=7;
   VoxelSection src=SectionSnapshotBuilder.build(new SectionPos(-12,4,31),ids);
   PersistentSectionCache cache=new PersistentSectionCache(root,"world-A","minecraft_overworld",8,1024*1024);
   cache.save(src);
   ChunkSnapshotStore restored=new ChunkSnapshotStore(16);
   if(cache.loadAll(restored,8)!=1) throw new AssertionError("cache did not reload");
   VoxelSection copy=restored.get(src.position());
   if(copy==null || copy.blockId(0,0,0)!=42 || copy.blockId(1,1,0)!=7) throw new AssertionError("palette data mismatch");
   if(restored.lodTree().findLeaf(src.position())==null) throw new AssertionError("not inserted into LOD tree");
   if(new PersistentSectionCache(root,"world-B","minecraft_overworld").loadAll(new ChunkSnapshotStore(),8)!=0)
    throw new AssertionError("world namespace leaked");
   if(new PersistentSectionCache(root,"world-A","minecraft_nether").loadAll(new ChunkSnapshotStore(),8)!=0)
    throw new AssertionError("dimension namespace leaked");
   cache.remove(src.position()); if(cache.fileCount()!=0) throw new AssertionError("remove failed");
   Files.createDirectories(cache.directory()); Files.write(cache.directory().resolve("broken.vms"),new byte[]{1,2,3});
   if(cache.loadAll(new ChunkSnapshotStore(),8)!=0) throw new AssertionError("corrupt entry accepted");
   PersistentSectionCache bounded=new PersistentSectionCache(root,"bounded","overworld",1,1024*1024);
   bounded.save(src); bounded.save(SectionSnapshotBuilder.build(new SectionPos(2,4,31),ids));
   if(bounded.fileCount()>1) throw new AssertionError("file-count budget exceeded");
   System.out.println("PersistentSectionCacheSmokeTest: PASS");
  } finally {
   try(var walk=Files.walk(root)){walk.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}
  }
 }
}
