package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;
import javax.imageio.ImageIO;

/** Own-art software preview. This does not capture or verify the Minecraft client. */
public final class MakeBossPreview {
    public static void main(String[] args) throws Exception {
        Path target=Path.of(args.length==0?"evidence/boss-v09-original-preview.png":args[0]);
        ImageAsset portrait=args.length>1?ImageAsset.fromPng(Files.readAllBytes(Path.of(args[1]))):null;
        Files.createDirectories(target.toAbsolutePath().getParent());
        var canvas=new BufferedImage(1152,704,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=canvas.createGraphics();
        try {
            graphics.setColor(new Color(0x0b121c));graphics.fillRect(0,0,canvas.getWidth(),canvas.getHeight());
            text(graphics,"TIDEFRAME / ORIGINAL GUARDIAN",64,26,3,0xe9d7ad);
            text(graphics,"SOFTWARE PREVIEW - NOT A MINECRAFT SCREENSHOT",64,62,2,0x8c9cae);
            int row=0;
            for(float health:new float[]{1,.75f,.25f}) {
                int top=110+row*184;
                String label="AURORA | "+Math.round(health*100)+"%"+(health<=.30f?" | ENRAGED":"");
                text(graphics,label,(canvas.getWidth()-textWidth(label)*3)/2,top,3,0xffe4ef);
                graphics.drawImage(BossBarAssets.preview(health,portrait),64,top+24,null);
                row++;
            }
        } finally { graphics.dispose(); }
        ImageIO.write(canvas,"PNG",target.toFile());
        System.out.println("ORIGINAL_BOSS_SOFTWARE_PREVIEW MinecraftScreenshot=false path="+target.toAbsolutePath());
    }
    private static int textWidth(String text) {
        int width=0;
        for(char character:text.toCharArray()) {
            if(character==' '){width+=4;continue;}
            int right=0;
            for(String row:PackGenerator.pattern(character).split("/"))
                for(int x=0;x<row.length();x++)if(row.charAt(x)=='1')right=Math.max(right,x+1);
            width+=right+1;
        }
        return width;
    }
    private static void text(Graphics2D graphics,String text,int left,int top,int scale,int rgb) {
        graphics.setColor(new Color(rgb));
        int x=left;
        for(char character:text.toCharArray()) {
            if(character==' '){x+=4*scale;continue;}
            String[] rows=PackGenerator.pattern(character).split("/");
            int right=0;
            for(int y=0;y<rows.length;y++)for(int column=0;column<rows[y].length();column++)if(rows[y].charAt(column)=='1') {
                graphics.fillRect(x+column*scale,top+y*scale,scale,scale);right=Math.max(right,column+1);
            }
            x+=(right+1)*scale;
        }
    }
}
