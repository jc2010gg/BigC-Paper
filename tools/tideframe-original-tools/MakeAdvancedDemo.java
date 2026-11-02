import dev.tideframe.core.*;
import dev.tideframe.edit.ScreenCodec;
import dev.tideframe.pack.HudRaster;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** All source pixels are original procedural geometry. No external artwork or fonts. */
class MakeAdvancedDemo {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args.length==0?".":args[0]);
        Path assets=root.resolve("examples/images"), layouts=root.resolve("examples/advanced-demo");
        Files.createDirectories(assets);Files.createDirectories(layouts);
        var art=new BufferedImage(3840,2160,BufferedImage.TYPE_INT_ARGB);
        var g=art.createGraphics();
        try {
            g.scale(15,15);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0,0,new Color(0x122539),200,144,new Color(0x3b807e)));
            g.fillRect(0,0,256,144);
            for(int i=0;i<11;i++) {
                int y=12+i*4;g.setColor(new Color(75+i*9,140+i*5,158+i*5,105));
                g.fillPolygon(new int[]{0,80,145,256,256,140,75,0},new int[]{y+20,y,y+8,y-3,y+1,y+12,y+4,y+24},8);
            }
            g.setColor(new Color(0x89c6c5));g.fillPolygon(new int[]{0,58,106,152,201,256,256,0},new int[]{112,45,103,54,114,75,144,144},8);
            g.setColor(new Color(0x24445b));g.fillPolygon(new int[]{0,42,85,140,186,256,256,0},new int[]{130,79,122,94,133,96,144,144},8);
            g.setColor(new Color(0xf9dda4));g.fillRect(221,18,8,8);
            g.setColor(new Color(0xb8eff0));
            for(int i=0;i<18;i++){int x=(i*37+11)%245,y=(i*11+7)%54;g.fillRect(x,y,1,1);}
        } finally {g.dispose();}
        var photo=asset(art);Files.write(assets.resolve("aurora-ridge-4k.png"),photo.png());
        var texture=new BufferedImage(640,96,BufferedImage.TYPE_INT_ARGB);g=texture.createGraphics();
        try {
            g.setPaint(new GradientPaint(0,0,new Color(0x1e5861),640,96,new Color(0x153744)));g.fillRect(0,0,640,96);
            g.setColor(new Color(0x86e0d6));g.fillPolygon(new int[]{18,38,18},new int[]{25,48,71},3);
            g.setColor(new Color(0x68b1b2));g.fillRect(54,0,2,96);g.fillRect(0,94,640,2);
        } finally {g.dispose();}
        var buttonImage=asset(texture);Files.write(assets.resolve("action-strip.png"),buttonImage.png());
        var elements=new ArrayList<Screen.Element>();
        elements.add(element("scenery","",0,.15,1.86,1.40,"decoration","",false,photo,new Screen.Style(.94,"#28384b","#ffffff","#ffffff",false),0,null));
        elements.add(element("glass","",0,-.55,1.88,.47,"decoration","",true,null,new Screen.Style(.66,"#102939","#75d9d4","#ffffff",true),1,null));
        elements.add(element("heading","AURORA RIDGE",0,.85,1.8,.16,"label","",false,null,new Screen.Style(1,"#28384b","#ffffff","#d5f4ed",false),3,null));
        elements.add(element("caption","ORIGINAL ART / INDEPENDENT HIT AREAS",0,-.90,1.9,.12,"label","",false,null,Screen.Style.defaults(),3,null));
        elements.add(element("profile","PROFILE",-.48,-.55,.78,.20,"button","open:profile",false,buttonImage,new Screen.Style(1,"#28384b","#96e4da","#ffffff",true),3,new Screen.ActionArea(-.48,-.55,.70,.18)));
        elements.add(element("back","BACK",.48,-.55,.78,.20,"button","open:menu",false,buttonImage,new Screen.Style(1,"#28384b","#96e4da","#ffffff",true),3,new Screen.ActionArea(.48,-.55,.70,.18)));
        for(int i=0;i<30;i++)elements.add(element("accent"+i,"",-.87+i*.06,.69,.018,.045,"decoration","",true,null,new Screen.Style(.25+(i%4)*.15,"#a4eee1","#ffffff","#ffffff",false),2,null));
        var profiles=List.of(RenderQuality.low(),RenderQuality.medium(),RenderQuality.high(),RenderQuality.custom(3840,2160));
        var ids=List.of("advanced_low","advanced_medium","advanced_high","advanced_4k");
        for(int i=0;i<profiles.size();i++) {
            var quality=profiles.get(i);
            var fittedPhoto=quality.width()==3840?photo:asset(resize(art,quality.width(),quality.height()));
            var fittedButton=quality.width()>=640?buttonImage:asset(resize(texture,quality.width(),Math.max(1,(int)Math.round(quality.width()*96.0/640))));
            var fittedElements=elements.stream().map(e->new Screen.Element(e.id(),e.text(),e.x(),e.y(),e.width(),e.height(),e.kind(),e.action(),
                    e.shape(),e.border(),e.messageCondition(),e.background(),e.image()==null?null:e.image().equals(photo)?fittedPhoto:fittedButton,e.style(),e.layer(),e.actionArea())).toList();
            var screen=new Screen(ids.get(i),"AURORA RIDGE",fittedElements,quality);
            Files.writeString(layouts.resolve(ids.get(i)+".json"),ScreenCodec.encode(screen)+"\n");
            if(profiles.get(i).preset().equals("high"))ImageIO.write(HudRaster.render(screen,null,8),"png",root.resolve("evidence/advanced-v06-raster.png").toFile());
        }
        System.out.println("ADVANCED_DEMOS_CREATED image3840x2160 elements="+elements.size()+" independentButtons=2 profiles=4 MinecraftScreenshot=false");
    }
    static ImageAsset asset(BufferedImage image) throws Exception {
        var bytes=new ByteArrayOutputStream();if(!ImageIO.write(image,"png",bytes))throw new IllegalStateException("PNG unavailable");
        return ImageAsset.fromPng(bytes.toByteArray());
    }
    static BufferedImage resize(BufferedImage image,int width,int height) {
        var fitted=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);var g=fitted.createGraphics();
        try {g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(image,0,0,width,height,null);}
        finally {g.dispose();}
        return fitted;
    }
    static Screen.Element element(String id,String text,double x,double y,double w,double h,String kind,String action,boolean bg,ImageAsset image,Screen.Style style,int layer,Screen.ActionArea area) {
        return new Screen.Element(id,text,x,y,w,h,kind,action,"rectangle",kind.equals("button"),null,bg,image,style,layer,area);
    }
}
