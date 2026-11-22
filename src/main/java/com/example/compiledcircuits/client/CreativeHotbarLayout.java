package com.example.compiledcircuits.client;

/** One geometry for both the in-world HUD and creative inventory, including narrow GUI scales. */
public record CreativeHotbarLayout(int left,int bottom,int columns,int cell,int count) {
    public static CreativeHotbarLayout of(int width,int height,int count){
        int columns=Math.min(19,count),cell=Math.min(20,Math.max(10,(width-16)/columns));
        return new CreativeHotbarLayout((width-columns*cell)/2,height-2,columns,cell,count);
    }
    public int x(int slot){return left+(slot%columns)*cell;}
    public int y(int slot){return bottom-cell-(slot/columns)*cell;}
    public int top(){return bottom-((count+columns-1)/columns)*cell;}
    public int hit(double x,double y){for(int i=0;i<count;i++)if(x>=x(i)&&x<x(i)+cell&&y>=y(i)&&y<y(i)+cell)return i;return -1;}
}
