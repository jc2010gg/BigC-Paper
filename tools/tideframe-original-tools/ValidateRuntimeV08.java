import dev.tideframe.core.*;
import dev.tideframe.core.demo.*;
import dev.tideframe.edit.*;
import dev.tideframe.pack.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Reproducible, local-only runtime registry/pack probe; never starts or writes a server. */
public final class ValidateRuntimeV08 {
    private static volatile long blackhole;
    private static final int LOOKUPS=10_000;
    private static final Pattern BITMAP=Pattern.compile("\"file\":\"tideframe:([^\"]+)\",\"ascent\":(-?[0-9]+),\"height\":([0-9]+),\"chars\":\\[\"\\\\u([0-9a-f]{4})\"\\]");
    private static final Pattern SPACE=Pattern.compile("\"\\\\u([0-9a-f]{4})\":(-?[0-9]+(?:\\.[0-9]+)?)");

    public static void main(String[] args) throws Exception {
        if(args.length<1||args.length>2)throw new IllegalArgumentException("Usage: ValidateRuntimeV08 <repository> [read-only screen directory]");
        Path repo=Path.of(args[0]).toAbsolutePath().normalize();
        require(Files.isRegularFile(repo.resolve("build.gradle.kts")),"Expected the Tideframe repository");
        Path source=args.length==2?Path.of(args[1]).toAbsolutePath().normalize():repo.resolve("../../work/tideframe-server/plugins/Tideframe/screens").normalize();
        Path output=repo.resolve(".build-tools/runtime-v08-benchmark"),evidence=repo.resolve("evidence");
        require(!output.startsWith(source)&&!source.startsWith(output),"Benchmark output must be separate from the server inputs");
        Files.createDirectories(output);Files.createDirectories(evidence);
        long started=System.nanoTime(),heapBefore=usedHeap();
        var store=new ScreenStore(source);var sourceIds=store.ids();
        require(sourceIds.size()==10,"Expected ten current operator JSON inputs, found "+sourceIds.size());
        var inputs=inventoryInputs(source,sourceIds);
        var screens=new TreeMap<String,Screen>(Screen.demos());
        // Match TideframePlugin startup: stored files override built-ins; runtime demos are putIfAbsent.
        for(String id:sourceIds)screens.put(id,store.load(id));
        var runtime=RuntimeDemoCatalog.screens();require(runtime.size()==48,"Expected 48 own runtime demo frames");
        runtime.forEach((id,screen)->screens.putIfAbsent(id,presentation(screen)));
        screens.replaceAll((id,screen)->CloseControl.prepare(screen));
        require(screens.size()==58,"Expected the same 58-screen production registry");
        var screenInventory=new ArrayList<Object>();int actions=0,menus=0,passive=0;
        for(var screen:screens.values()) {
            String encoded=ScreenCodec.encode(screen);
            require(ScreenCodec.decode(encoded).equals(screen),"Roundtrip failed: "+screen.id());
            if(screen.presentation().mode().equals("overlay"))passive++;else menus++;
            for(var element:screen.elements())if(element.kind().equals("button")) {
                for(boolean condition:List.of(false,true))validateAction(MessageRules.action(element,permission->condition),screens);
                actions++;
            }
            screenInventory.add(row("id",screen.id(),"elements",screen.elements().size(),"mode",screen.presentation().mode(),
                "scale",screen.presentation().scale(),"cursor",screen.presentation().cursor(),"quality",screen.quality().preset(),
                "canonicalJsonBytes",encoded.getBytes(StandardCharsets.UTF_8).length,"canonicalSha256",sha(encoded.getBytes(StandardCharsets.UTF_8))));
        }
        var trajectories=verifyPrograms(screens);
        long libraryEnd=System.nanoTime();
        var images=ImageGlyphs.create(screens.values().stream().flatMap(screen->screen.elements().stream()).map(Screen.Element::image).filter(Objects::nonNull).toList());
        long bakeStart=System.nanoTime();var hud=HudAssets.create(screens.values());long bakeEnd=System.nanoTime();
        long packStart=System.nanoTime();var artifact=PackGenerator.generate(output.resolve("packs"),images,hud);long packEnd=System.nanoTime();
        long heapAfterGeneration=usedHeap();
        var entries=hud.entries();var advances=fontAdvances(entries);
        int frameChecks=0,cursorChecks=0;
        for(var screen:screens.values()) {
            for(int step=0;step<=8;step++){validateSequence(hud.frame(screen,null,step),advances);frameChecks++;}
            for(var element:screen.elements())if(element.kind().equals("button")){validateSequence(hud.frame(screen,element.id(),8),advances);frameChecks++;}
            for(double x:new double[]{-1,0,1})for(double y:new double[]{-1,0,1}){validateSequence(hud.cursor(screen,x,y),advances);cursorChecks++;}
        }
        var list=List.copyOf(screens.values());
        String[] hovers=list.stream().map(screen->screen.elements().stream().filter(e->e.kind().equals("button")).map(Screen.Element::id).findFirst().orElse(null)).toArray(String[]::new);
        for(int i=0;i<LOOKUPS;i++)blackhole+=lookup(hud,list,hovers,i);
        long[] samples=new long[LOOKUPS];long totalStart=System.nanoTime(),checksum=0;
        for(int i=0;i<LOOKUPS;i++){long begin=System.nanoTime();checksum+=lookup(hud,list,hovers,i);samples[i]=System.nanoTime()-begin;}
        long lookupElapsed=System.nanoTime()-totalStart;blackhole+=checksum;Arrays.sort(samples);
        var zipEntries=new ArrayList<Object>();int pngs=0,maxWidth=0,maxHeight=0;
        try(var zip=new ZipFile(artifact.path().toFile())) {
            var names=zip.stream().map(entry->entry.getName()).sorted().toList();
            for(String name:names) {
                var entry=zip.getEntry(name);byte[] bytes;
                try(var stream=zip.getInputStream(entry)){bytes=stream.readAllBytes();}
                var item=row("path",name,"bytes",bytes.length,"sha256",sha(bytes));
                if(name.endsWith(".png")) {
                    var image=ImageIO.read(new ByteArrayInputStream(bytes));require(image!=null,"Invalid pack PNG: "+name);
                    require(image.getWidth()<=256&&image.getHeight()<=256,"Pack PNG exceeds vanilla atlas bounds: "+name);
                    item.put("width",image.getWidth());item.put("height",image.getHeight());pngs++;
                    maxWidth=Math.max(maxWidth,image.getWidth());maxHeight=Math.max(maxHeight,image.getHeight());
                }
                zipEntries.add(item);
            }
        }
        require(sourceIds.equals(store.ids())&&inputs.equals(inventoryInputs(source,sourceIds)),"Operator source JSON changed during the read-only probe");
        String packSha=HexFormat.of().formatHex(artifact.sha256());require(packSha.equals(sha(Files.readAllBytes(artifact.path()))),"Artifact SHA-256 mismatch");
        long packBytes=Files.size(artifact.path());require(packBytes<=HudAssets.MAX_PACK_BYTES,"Pack exceeded the existing 64 MiB guard");
        var imageInventory=images.images().stream().map(image->row("sha256",image.sha256(),"width",image.width(),"height",image.height(),"pngBytes",image.png().length)).toList();
        var report=row("schema",1,"generatedAtUtc",Instant.now().toString(),"scope","LOCAL_HEADLESS_REGISTRY_PACK_AND_LOOKUP_PROBE",
            "minecraftClientConnected",false,"networkLatencyMeasured",false,"visualAcceptanceVerified",false,"serverStarted",false,"sourceFilesWritten",false,
            "javaVersion",System.getProperty("java.version"),"os",System.getProperty("os.name"),"heapMaximumBytes",Runtime.getRuntime().maxMemory(),
            "sourceDirectory",source.toString(),"sourceInputCount",sourceIds.size(),"sourceInputs",inputs,"sourceInputsUnchanged",true,
            "screens",screens.size(),"menus",menus,"passiveOverlays",passive,"roundtrips",screens.size(),"buttonsAndActionsChecked",actions,
            "frameLookupsVerified",frameChecks,"cursorLookupsVerified",cursorChecks,"screenInventory",screenInventory,"programTrajectories",trajectories,
            "uniqueSourceImages",images.size(),"sourceImages",imageInventory,"hudBitmapProviders",advances.size()-2048,"hudSpaceProviders",2048,
            "packBytes",packBytes,"packSha256",packSha,"packSha1",HexFormat.of().formatHex(artifact.sha1()),"packPath",artifact.path().toString(),
            "zipEntryCount",zipEntries.size(),"pngEntries",pngs,"maximumPngWidth",maxWidth,"maximumPngHeight",maxHeight,"packEntries",zipEntries,
            "libraryLoadAndValidationMs",milliseconds(libraryEnd-started),"hudBakeMs",milliseconds(bakeEnd-bakeStart),"packGenerationMs",milliseconds(packEnd-packStart),
            "heapUsedBeforeBytes",heapBefore,"heapUsedAfterGenerationBytes",heapAfterGeneration,"heapUsedAfterProbeBytes",usedHeap(),
            "heapMeasurementNote","Instantaneous used heap = totalMemory - freeMemory; includes retained garbage and is not peak memory. No forced GC.",
            "lookupMeasurement",row("operation","one immutable frame selection plus one cursor selection, including String creation; excludes I/O, rasterization, scheduling, packets and client rendering",
                "warmupIterations",LOOKUPS,"measuredIterations",LOOKUPS,"totalElapsedNs",lookupElapsed,"meanObservedNs",lookupElapsed/(double)LOOKUPS,
                "sampleMinNs",samples[0],"sampleMedianNs",samples[LOOKUPS/2],"sampleP95Ns",samples[(int)(LOOKUPS*.95)-1],"sampleMaxNs",samples[LOOKUPS-1],
                "checksum",checksum,"note","Single-process local microbenchmark with System.nanoTime overhead; not a server tick, FPS, ping or multiplayer responsiveness measurement."),
            "totalProbeMs",milliseconds(System.nanoTime()-started));
        Path receipt=evidence.resolve("runtime-v08-benchmark.json");Files.writeString(receipt,json(report)+"\n",StandardCharsets.UTF_8);
        System.out.println("TIDEFRAME_RUNTIME_BENCHMARK_PASSED screens="+screens.size()+" inputs="+sourceIds.size()+" actions="+actions+" roundtrips="+screens.size()+" providers="+(advances.size()-2048)+" pngs="+pngs+" maxPng="+maxWidth+"x"+maxHeight);
        System.out.println("packBytes="+packBytes+" packSha256="+packSha+" hudBakeMs="+milliseconds(bakeEnd-bakeStart)+" packGenerationMs="+milliseconds(packEnd-packStart));
        System.out.println("localLookups="+LOOKUPS+" meanObservedNs="+(lookupElapsed/(double)LOOKUPS)+" sampleP95Ns="+samples[(int)(LOOKUPS*.95)-1]+" client=false network=false peakHeapMeasured=false");
        System.out.println("Evidence: "+receipt);
    }

    private static Screen presentation(Screen screen) {
        boolean overlay=screen.id().equals("overlay_demo");
        return new Screen(screen.id(),screen.title(),screen.elements(),screen.quality(),new Screen.Presentation(overlay?"overlay":"menu",overlay?1:2,overlay?"text":"block"));
    }
    private static List<Object> inventoryInputs(Path source,List<String> ids) throws Exception {
        var files=new ArrayList<Object>();for(String id:ids){byte[] bytes=Files.readAllBytes(source.resolve(id+".json"));files.add(row("id",id,"bytes",bytes.length,"sha256",sha(bytes)));}return files;
    }
    private static void validateAction(String action,Map<String,Screen> screens) {
        int[] calls={0};ActionRouter.dispatch(action,true,()->calls[0]++,id->{require(screens.containsKey(id),"Missing open target: "+id);calls[0]++;},message->calls[0]++);
        require(action.isEmpty()?calls[0]==0:calls[0]==1,"Unsupported action: "+action);
    }
    private static Map<String,Object> verifyPrograms(Map<String,Screen> screens) {
        var sequence=new SequenceGame();var frames=new LinkedHashSet<String>();frames.add(sequence.frameId());
        require(!sequence.click("D"),"Wrong initial letter must not advance");
        for(String letter:SequenceGame.ORDER){require(sequence.click(letter),"Correct letter rejected: "+letter);frames.add(sequence.frameId());for(int i=0;i<3;i++)frames.add(sequence.tick());}
        require(sequence.finished()&&frames.size()==17,"Expected 17 stable/fading sequence frames");
        frames.forEach(id->require(screens.containsKey(id),"Missing sequence frame "+id));
        sequence.reset();require(sequence.frameId().equals("sequence_demo"),"Sequence reset failed");
        var winners=new ArrayList<Object>();
        for(var color:RouletteSpin.Color.values()) {
            var roulette=new RouletteSpin(color);require(screens.containsKey(roulette.frameId()),"Missing initial roulette frame");
            for(int tick=0;tick<RouletteSpin.DURATION_TICKS;tick++)require(screens.containsKey(roulette.tick()),"Missing roulette frame");
            String expected="win_"+color.name().toLowerCase(Locale.ROOT);require(roulette.finished()&&roulette.frameId().equals(expected),"Roulette did not reach code-selected result");
            winners.add(row("requestedColor",color.name(),"resultFrame",roulette.frameId(),"animationUpdates",RouletteSpin.DURATION_TICKS));
        }
        return row("sequenceOrder",SequenceGame.ORDER,"sequenceFrames",List.copyOf(frames),"wrongInputIgnored",true,"resetVerified",true,"rouletteResults",winners);
    }
    private static Map<Character,Integer> fontAdvances(Map<String,byte[]> entries) throws Exception {
        byte[] fontBytes=entries.get("assets/tideframe/font/hud.json");require(fontBytes!=null,"Missing HUD font");String font=new String(fontBytes,StandardCharsets.UTF_8);
        var advances=new HashMap<Character,Integer>();var spaces=SPACE.matcher(font);
        while(spaces.find()){char glyph=(char)Integer.parseInt(spaces.group(1),16);double quarter=Double.parseDouble(spaces.group(2))*4;require(quarter==Math.rint(quarter),"Space advance must be quarter-pixel exact");require(advances.put(glyph,(int)quarter)==null,"Duplicate space glyph");}
        require(advances.size()==2048,"Expected 2048 own quarter-pixel spaces");var bitmaps=BITMAP.matcher(font);int providers=0;
        while(bitmaps.find()) {
            String path="assets/tideframe/textures/"+bitmaps.group(1);byte[] bytes=entries.get(path);require(bytes!=null,"Missing provider PNG: "+path);
            var image=ImageIO.read(new ByteArrayInputStream(bytes));require(image!=null&&image.getWidth()<=256&&image.getHeight()<=256,"Provider PNG exceeds vanilla bounds");
            int ascent=Integer.parseInt(bitmaps.group(2)),height=Integer.parseInt(bitmaps.group(3));require(ascent<=height&&height>0,"Invalid bitmap ascent/height");int inkWidth=0;
            for(int x=0;x<image.getWidth();x++)for(int y=0;y<image.getHeight();y++)if((image.getRGB(x,y)>>>24)!=0)inkWidth=Math.max(inkWidth,x+1);
            char glyph=(char)Integer.parseInt(bitmaps.group(4),16);require(glyph>=0xe000&&glyph<=0xe7ff||glyph>=0xf000&&glyph<=0xf8ff,"Visual glyph outside reserved range");
            require(advances.put(glyph,(Math.round(inkWidth*((float)height/image.getHeight()))+1)*4)==null,"Duplicate visual glyph");providers++;
        }
        require(providers>0&&providers<=4352,"Visual provider limit exceeded");return advances;
    }
    private static void validateSequence(String sequence,Map<Character,Integer> advances) {
        long total=0;for(char glyph:sequence.toCharArray()){Integer value=advances.get(glyph);require(value!=null,"Unregistered HUD glyph");total+=value;}
        require(total==0,"HUD sequence has nonzero advance: "+total);
    }
    private static long lookup(HudAssets hud,List<Screen> screens,String[] hovers,int iteration) {
        int index=iteration%screens.size();var screen=screens.get(index);double x=(iteration%257)/128.0-1,y=((iteration*17)%145)/72.0-1;
        return hud.frame(screen,iteration%2==0?hovers[index]:null,8).length()+hud.cursor(screen,x,y).length();
    }
    private static long usedHeap(){return Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();}
    private static double milliseconds(long nanos){return nanos/1_000_000.0;}
    private static String sha(byte[] bytes) throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static LinkedHashMap<String,Object> row(Object... fields){var map=new LinkedHashMap<String,Object>();for(int i=0;i<fields.length;i+=2)map.put((String)fields[i],fields[i+1]);return map;}
    private static String json(Object value) {
        if(value==null)return "null";if(value instanceof Boolean||value instanceof Number)return value.toString();
        if(value instanceof Map<?,?> map){var items=new ArrayList<String>();map.forEach((key,item)->items.add(json(key.toString())+":"+json(item)));return "{"+String.join(",",items)+"}";}
        if(value instanceof Collection<?> collection)return "["+String.join(",",collection.stream().map(ValidateRuntimeV08::json).toList())+"]";
        String string=value.toString();var result=new StringBuilder("\"");for(int i=0;i<string.length();i++){char c=string.charAt(i);switch(c){case '\\'->result.append("\\\\");case '"'->result.append("\\\"");case '\n'->result.append("\\n");case '\r'->result.append("\\r");case '\t'->result.append("\\t");default->{if(c<32)result.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else result.append(c);}}}return result.append('"').toString();
    }
}
