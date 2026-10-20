package com.example.compiledcircuits.client;

/** Shared row geometry for drawing, hit testing and scroll bounds. No GUI objects. */
final class ManagerLayout {
    static final int TOP=55, LIST_TOP=92, SEARCH_WIDTH=190, SEARCH_HEIGHT=20,
            SEARCH_ROW_HEIGHT=30, SEARCH_FILTER_WIDTH=72, SEARCH_GAP=4, BOTTOM_MARGIN=40,
            FOLDER_ROW_HEIGHT=18, NETWORK_ROW_HEIGHT=31, BROKEN_ROW_HEIGHT=36;
    record Rows(int left, int right, int bottom, int rowHeight, boolean inclusiveRight) {
        int count() { return Math.max(0, (bottom-LIST_TOP)/rowHeight); }
        boolean contains(double x, double y) {
            return x>=left && (inclusiveRight ? x<=right : x<right) && y>=LIST_TOP && y<bottom;
        }
        int index(double x, double y, int scroll, int size) {
            if (!contains(x,y)) return -1;
            int row=(int)((y-LIST_TOP)/rowHeight), index=scroll+row;
            return row<count() && index>=0 && index<size ? index : -1;
        }
        int y(int visibleRow) { return LIST_TOP+visibleRow*rowHeight; }
        int clamp(int scroll, int size) { return Math.max(0,Math.min(scroll,Math.max(0,size-count()))); }
    }
    int width, height, folderPanelWidth;
    void resize(int width,int height) { this.width=width;this.height=height;folderPanelWidth=Math.max(140,(int)(width*0.38F)); }
    Rows folders() { return new Rows(7,folderPanelWidth-2,height-BOTTOM_MARGIN,FOLDER_ROW_HEIGHT,false); }
    Rows networks() { return new Rows(folderPanelWidth+8,width-8,height-BOTTOM_MARGIN,NETWORK_ROW_HEIGHT,true); }
    Rows search() { return new Rows(10,width-10,height-BOTTOM_MARGIN,SEARCH_ROW_HEIGHT,true); }
    Rows broken() { return new Rows(10,width-10,height-64,BROKEN_ROW_HEIGHT,false); }
    // Preserve the pre-refactor broken-list wheel region until the separate GUI boundary fix.
    boolean brokenWheelContains(double x, double y) { return x>=10 && x<=width-10 && y>=TOP && y<broken().bottom(); }
    void clamp(ManagerModel model,ManagerState state) {
        state.folderScroll=folders().clamp(state.folderScroll,model.visibleFolderRows.size());
        state.networkScroll=networks().clamp(state.networkScroll,model.visibleNetworks.size());
        state.searchScroll=search().clamp(state.searchScroll,model.searchResults.size());
        state.brokenScroll=broken().clamp(state.brokenScroll,model.brokenEntries.size());
    }
}
