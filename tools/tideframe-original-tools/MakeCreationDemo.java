import dev.tideframe.core.*;
import dev.tideframe.edit.ScreenCodec;
import dev.tideframe.pack.HudRaster;
import java.nio.file.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Original layout and palette; only our procedural Aurora Ridge image is reused. */
class MakeCreationDemo {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args.length==0?".":args[0]), target=root.resolve("examples/creation-demo");
        Files.createDirectories(target);
        var original=ImageIO.read(root.resolve("examples/images/aurora-ridge-4k.png").toFile());
        var photo=MakeAdvancedDemo.asset(MakeAdvancedDemo.resize(original,512,288));
        var screen=new Screen("creation_demo","CREATE YOUR UI",List.of(
            e("panel","",0,.05,1.88,1.65,"decoration","",true,null,style(.91,0,.03,1,null,"none","center",0),0,null),
            e("photo","",.52,.21,.68,.80,"decoration","",false,photo,style(.92,-6,.02,1,null,"none","center",0),1,null),
            e("heading","MAKE IT YOURS",-.24,.73,1.32,.16,"label","",false,null,style(1,0,.04,1,null,"none","left",0),3,null),
            e("description","YOUR LAYOUT.\nYOUR COLOURS.\nYOUR ACTIONS.",-.39,.29,.94,.52,"label","",false,null,style(1,0,.02,1,null,"none","left",105),2,null),
            e("tag","ORIGINAL",.52,-.27,.63,.16,"label","",true,null,style(.88,-6,.02,2,null,"none","center",0),3,null),
            e("note","ROTATE / WRAP / HOVER",0,-.43,1.78,.11,"label","",false,null,style(.75,0,0,0,null,"none","center",0),3,null),
            e("message","SAY HELLO",-.49,-.65,.76,.20,"button","message:Your original UI is ready.",true,null,style(1,0,.05,2,"#f2d5a7","scale","center",0),4,new Screen.ActionArea(-.49,-.65,.76,.20)),
            e("back","BACK",.49,-.65,.76,.20,"button","open:menu",false,null,style(1,0,0,0,"#f2d5a7","translate","center",0),4,new Screen.ActionArea(.49,-.65,.76,.20))
        ),RenderQuality.low());
        screen.validateActionAreas();
        String json=ScreenCodec.encode(screen);
        if(!json.startsWith("{\"version\":5,"))throw new IllegalStateException("Expected schema5");
        var decoded=ScreenCodec.decode(json);
        if(!decoded.equals(screen))throw new IllegalStateException("Demo roundtrip changed content");
        Files.writeString(target.resolve("creation_demo.json"),json+"\n");
        ImageIO.write(HudRaster.render(decoded,null,8),"png",root.resolve("evidence/creation-v07-raster.png").toFile());
        ImageIO.write(HudRaster.render(decoded,"message",8),"png",root.resolve("evidence/creation-v07-hover.png").toFile());
        System.out.println("CREATION_DEMO_VALIDATED version5 elements="+screen.elements().size()+" source512x288 MinecraftScreenshot=false");
    }
    static Screen.Style style(double opacity,double rotation,double radius,int outline,String hover,String effect,String align,int wrap) {
        return new Screen.Style(opacity,"#173b48","#8bdfc9","#edf8f4",false,rotation,radius,outline,hover,effect,align,wrap);
    }
    static Screen.Element e(String id,String text,double x,double y,double w,double h,String kind,String action,boolean bg,ImageAsset image,Screen.Style style,int layer,Screen.ActionArea area) {
        return new Screen.Element(id,text,x,y,w,h,kind,action,"rounded",!kind.equals("decoration")&&!id.equals("back"),null,bg,image,style,layer,area);
    }
}
