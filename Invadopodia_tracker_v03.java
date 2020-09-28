import ij.plugin.filter.*;
import ij.*;
import ij.gui.*;
import ij.process.*;

// Starting code: Invadopodia_tracker_v02.java
// Output on a single line, if suppress1 and suppress2 are checked

// Ved P. Sharma, April 02, 2013

public class Invadopodia_tracker_v03 implements PlugInFilter {

    private static int newNT = 500, NT, oldNT, startNT, delNT = 20, rad = 5, minInpods = 10, maxInpods = 30, maxItr=200;
    private ImagePlus imp;
    private int width, height, slices;
    private int[] u,v; // starting points selected by the user
    private int[] x,y; // x,y coordinates of totalPts at every slice
    private int startSlice, currSlice, startInpods, currInpods, totalPts, oldTotalPts;
    private int[] start, end; // arrays to store the start frame# and end frame# of each inpod
    private Roi roiStart, roi;
	private FloatPolygon fp;
	private int nw, n, ne, w, e, sw, s, se, sum1, sum2, sum3, sum4; 
	private int[][] traj_x, traj_y;
	private boolean[] inpodState; //alive(=true) or dead(=false)
	private boolean suppress1, suppress2;
	private int i, j;
	
    public int setup(String arg, ImagePlus imp) {
        this.imp = imp;
        if(imp==null) {
        	IJ.error("Invadopodia tracker", "    Error: No image found.    ");
        	return DONE;
        }
        else
        	return DOES_16|DOES_8G|STACK_REQUIRED|NO_UNDO|NO_CHANGES;
    }

    public void run(ImageProcessor ip) {
		imp.unlock();
		roiStart = imp.getRoi();
		if(roiStart==null || (roiStart.getType()!=Roi.POINT)) {
			IJ.error("Invadopodia tracker", "   Error: A point ROI required.   ");
			return;
		}
		fp = roiStart.getFloatPolygon();
		startInpods = fp.npoints;
    	u = new int[startInpods]; v = new int[startInpods];
		for(i=0;i<startInpods;i++) {
			u[i] = (int)fp.xpoints[i]; v[i] = (int)fp.ypoints[i];
		}
		if(!showDialog()) return;
// Since user input went OK, let's start the stop watch and allocate space to some variables
		long startTime = System.currentTimeMillis();		
		width = imp.getWidth();	height = imp.getHeight(); slices = imp.getStackSize();
		traj_x = new int[slices][startInpods]; traj_y = new int[slices][startInpods];
		inpodState = new boolean[startInpods]; // for keeping track of inpods at the currSlice; alive(=true) or dead(=false)
		for(i=0;i<startInpods;i++)
			inpodState[i] = true; // in the beginning all user selected inpods are alive (=true)
		currInpods = startInpods; // currInpods to keep track of when the total # drops to zero
// start slice		
		startSlice = imp.getCurrentSlice();
		currSlice = startSlice;
		if(!findMaxPoints())
			return;
		startNT = newNT;
		findInpodInsideCircle(u, v, rad);
		if(currInpods == 0) {
			IJ.run("Select None");
			imp.setRoi(roiStart);
			IJ.error("Invadopodia tracker", "No invadopod(s) found within "+rad+" pixels of where user clicked.");
			return;
		}
// track stack backward
		if(currSlice>1) {
			do {
				imp.setSlice(--currSlice);
				IJ.run("Select None");
				if(!findMaxPoints())
					return;
				findInpodInsideCircle(traj_x[currSlice], traj_y[currSlice], rad);
			} while(currInpods>0 && currSlice>1);
		}
// reassign variables before tracking forward
		for(i=0;i<startInpods;i++)
			inpodState[i] = true;
		currInpods = startInpods;
		currSlice = startSlice;
		imp.setSlice(currSlice);
		newNT = startNT;
// track stack forward
		if(currSlice<slices) {
			do {
				imp.setSlice(++currSlice);
				IJ.run("Select None");
				if(!findMaxPoints()) return;
				findInpodInsideCircle(traj_x[currSlice-2], traj_y[currSlice-2], rad);
			} while(currInpods>0 && currSlice<slices);
		}
		IJ.run("Select None");
		imp.setSlice(startSlice);
//		imp.setRoi(roiStart);
// Find out the start frame# and end frame# for each of the inpod
		start = new int[startInpods]; end = new int[startInpods];
		for(j=0;j<startInpods;j++)
			for(i=0;i<slices;i++)
				if(traj_x[i][j] != 0 && traj_y[i][j] != 0) {
					start[j] = i+1;
					break;
				}
		for(j=0;j<startInpods;j++)
			for(i=slices-1;i>=0;i--)
				if(traj_x[i][j] != 0 && traj_y[i][j] != 0) {
					end[j] = i+1;
					break;
				}
// Print track coordinates
		double pSize = imp.getCalibration().getX(1.0);
		if(!suppress1) {
			IJ.log("unit = "+imp.getCalibration().getUnit());
			IJ.log(""+startInpods+" invadopod(s) tracked in "+((System.currentTimeMillis()-startTime)/1000.0)+" sec");
		}
		for(j=0;j<startInpods;j++){
//			IJ.log(" ");
//			IJ.log("----------------------");
			if(suppress2)
				IJ.log("Track "+(j+1)+"("+start[j]+"-"+end[j]+")"+"\t"+start[j]+"\t"+end[j]);
			else {
				IJ.log("Track "+(j+1)+"("+start[j]+"-"+end[j]+")");
				IJ.log("\t"+"\t "+"\t "+start[j]+"\t"+end[j]);
			}
//			IJ.log(start[j]+"\t"+end[j]);
			if(!suppress2) {
				for(i=0;i<slices;i++)
					if(traj_x[i][j] != 0 && traj_y[i][j] != 0)
						IJ.log((i+1)+"\t"+(traj_x[i][j]*pSize)+"\t"+(traj_y[i][j]*pSize));
			}
		}
    }
//***********************************************
    void findInpodInsideCircle(int[] p, int[] q, int rad) {
    	for(j=0;j<p.length;j++){
    		if(inpodState[j]==true){
	    		imp.setRoi(new EllipseRoi(p[j]-rad,q[j],p[j]+rad,q[j],1.0));
	    		roi = imp.getRoi();
	    		for(i=0;i<x.length;i++) {
	    			if (roi.contains(x[i],y[i])) {
	    				traj_x[currSlice-1][j] = x[i];
	    				traj_y[currSlice-1][j] = y[i];
	    				imp.setRoi(new EllipseRoi(x[i]-rad,y[i],x[i]+rad,y[i],1.0));
	    				IJ.run("Add Selection...", "stroke=red width=1");
	    				Overlay overlay = imp.getOverlay();
	    				int size = overlay.size();
	    				overlay.get(size-1).setPosition(currSlice);
	    				break;
	    			}
	    		}
    		}
    	}
    	for(j=0;j<p.length;j++){
    		if(inpodState[j]==true){
				if((traj_x[currSlice-1][j]==0) && (traj_y[currSlice-1][j]==0)){
					inpodState[j] = false;
					currInpods--;
				}
    		}
    	}
    	return;
    }
//***********************************************
    boolean findMaxPoints() {
// It is better to get the oldTotalPts seed value this way, rather than an if(count==1) statement inside the do loop
    	IJ.run("Find Maxima...", "noise="+newNT+" output=[Point Selection]");
		oldTotalPts = imp.getRoi().getFloatPolygon().npoints;
    	int count = 0;
    	do {
    		if (count++ > maxItr){
    			IJ.error("Found "+totalPts+" inpods, which is outside the range("+minInpods+" - "+maxInpods+")\nTotal iterations: "+maxItr);
    			return false;
    		}
//IJ.log("iteration = "+count+" delNT ="+delNT+" newNT ="+newNT+" totalPts ="+totalPts);    		
    		NT = newNT;
    		IJ.run("Find Maxima...", "noise="+NT+" output=[Point Selection]");
    		fp = imp.getRoi().getFloatPolygon();
    		totalPts = fp.npoints;
    		
    		if ((oldTotalPts<minInpods && totalPts>maxInpods) || (oldTotalPts>maxInpods && totalPts<minInpods)){
    			if(delNT>1)
    				delNT=delNT/2;
    			else {
	    			IJ.error("Invadopodia tracker", "Increase the Invadopodia range.\nCurrent range: "+minInpods+" - "+maxInpods+"\n \n"+totalPts+" invadopodia at NT = "+NT+" (current iteration)\n"+oldTotalPts+" invadopodia at NT = "+oldNT+" (previous iteration)\ndelta NT = "+delNT+"\nTotal iterations at current slice: "+count);
	    			return false;
    			}
    		}
    		if (totalPts > maxInpods) {
    			oldTotalPts = totalPts;
    			oldNT = NT;
    			newNT = NT + delNT;
    		}
    		if (totalPts < minInpods){ 
    			oldTotalPts = totalPts; 
    			oldNT = NT;
    			newNT = NT - delNT;
    		}
    	} while ((totalPts<minInpods) || (totalPts>maxInpods));
	    fineTunePoints();
    	return true;
    }
//***********************************************
    void fineTunePoints() {
		fp = imp.getRoi().getFloatPolygon();
		totalPts = fp.npoints;
    	x = new int[totalPts]; y = new int[totalPts];
		for (i=0; i<totalPts; i++) {
			x[i] = (int)(fp.xpoints[i]);
			y[i] = (int)(fp.ypoints[i]);
		}
		ImageProcessor ip = imp.getProcessor();    	
    	for(i=0;i<totalPts;i++) {
    		if (!edgePoint(x[i], y[i])) {
    			nw=ip.getPixel(x[i]-1, y[i]-1); n=ip.getPixel(x[i], y[i]-1); ne=ip.getPixel(x[i]+1, y[i]-1);
    			w=ip.getPixel(x[i]-1, y[i]); e=ip.getPixel(x[i]+1, y[i]);
    			sw=ip.getPixel(x[i]-1, y[i]+1); s=ip.getPixel(x[i], y[i]+1); se=ip.getPixel(x[i]+1, y[i]+1);
    			sum1 = nw+n+w; sum2 = n+ne+e;
    			sum3 = e+se+s; sum4 = w+s+sw;
    			if (sum2>=sum1 && sum2>=sum3 && sum2>=sum4)
    				x[i] = x[i]+1;
    			if (sum3>=sum1 && sum3>=sum2 && sum3>=sum4) {
    				x[i] = x[i]+1;
    				y[i] = y[i]+1;
    			}
    			if (sum4>=sum1 && sum4>=sum2 && sum4>=sum3)
    				y[i] = y[i]+1;
    		}
    	}
    }
//***********************************************
    boolean edgePoint(int a, int b) {
    	return ((a-1) < 0 || (b-1) < 0 || (a+1) >= width || (b+1) >= height)? true:false;
    }
//***********************************************
    boolean showDialog() {
    	GenericDialog gd = new GenericDialog("Invadopodia tracker...");
        gd.addNumericField("Max invadopod displacement (pixels):",rad, 0);
        gd.addNumericField("Estimate of min no. of invadopods:",minInpods, 0);
        gd.addNumericField("Estimate_ of max no. of invadopods:",maxInpods, 0);
        gd.addMessage(" ");
        gd.addNumericField("Noise tolerance (NT):",newNT, 0);
        gd.addNumericField("Delta noise toelrance:",delNT, 0);
        gd.addNumericField("Max_ iterations:",maxItr, 0);
        gd.addCheckbox("Suppress info about length unit and tracking time", true);
        gd.addCheckbox("Suppress_ info about invadopodia coordinates", false);
        gd.setOKLabel("Track");
        gd.addHelp("https://sites.google.com/site/vedsharma/InvadopodTracker");
        gd.showDialog();
        if (gd.wasCanceled())
            return false;
        else {
        	rad = (int)gd.getNextNumber(); if (rad<=0) rad = 1;
        	minInpods = (int)gd.getNextNumber(); if (minInpods<=0) minInpods = 1;
        	maxInpods = (int)gd.getNextNumber(); if (maxInpods<=0) maxInpods = 1;
        	newNT = (int)gd.getNextNumber(); if (newNT<0) newNT = 0;
        	delNT = (int)gd.getNextNumber(); if (delNT<=0) delNT = 1;
        	maxItr = (int)gd.getNextNumber(); if (maxItr<=0) maxItr = 1;
        	suppress1 = gd.getNextBoolean();
        	suppress2 = gd.getNextBoolean();
        	if(gd.invalidNumber()) {
        		IJ.error("Invadopodia tracker", "Error: Invalid input value(s).");
        		return false;
        	}
        	return true;
        }
    }
}
// code for taking the totalPts close to maxInpods (untested!)
/*
if(totalPts>=minInpods && totalPts<=maxInpods){
	do{
		do {
			NT = NT - delNT;
    		IJ.run("Find Maxima...", "noise="+NT+" output=[Point Selection]");
    		fp = imp.getRoi().getFloatPolygon();
    		totalPts = fp.npoints;
		} while (totalPts<maxInpods);
		delNT = delNT/2;
		do {
			NT = NT + delNT;
    		IJ.run("Find Maxima...", "noise="+NT+" output=[Point Selection]");
    		fp = imp.getRoi().getFloatPolygon();
    		totalPts = fp.npoints;
		} while (totalPts>maxInpods);
	} while(delNT>1);
}
*/  		
