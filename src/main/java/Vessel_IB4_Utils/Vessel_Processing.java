package Vessel_IB4_Utils;

import Vessel_IB4_StardistOrion.StarDist2D;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.Duplicator;
import ij.plugin.ImageCalculator;
import ij.plugin.RGBStackMerge;
import ij.plugin.ZProjector;
import ij.plugin.filter.Analyzer;
import ij.process.AutoThresholder;
import java.awt.Color;
import java.awt.Font;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import javax.swing.ImageIcon;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom2.BoundingBox;
import mcib3d.geom2.Object3DComputation;
import mcib3d.geom2.Object3DInt;
import mcib3d.geom2.Object3DPlane;
import mcib3d.geom2.Objects3DIntPopulation;
import mcib3d.geom2.Objects3DIntPopulationComputation;
import mcib3d.geom2.VoxelInt;
import mcib3d.geom2.measurements.MeasureIntensity;
import mcib3d.geom2.measurements.MeasureVolume;
import mcib3d.image3d.ImageHandler;
import mcib3d.image3d.ImageInt;
import mcib3d.image3d.ImageLabeller;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import org.apache.commons.io.FilenameUtils;


/**
 * @author ORION-CIRB
 */
public class Vessel_Processing {
    
    private CLIJ2 clij2 = CLIJ2.getInstance();
    ImageIcon icon = new ImageIcon(this.getClass().getResource("/Orion_icon.png"));
    
    String[] channelNames = {"Vessels", "RNA"};
    public Calibration cal;
    private double pixVol;

    // Stardist
    private Object syncObject = new Object();
    private final double stardistPercentileBottom = 0.2;
    private final double stardistPercentileTop = 99.8;
    private final double stardistProbThresh = 0.25;
    private final double stardistOverlayThresh = 0.25;
    private final File stardistModelsPath = new File(IJ.getDirectory("imagej")+File.separator+"models");
    private String stardistFociModel = "fociRNA-1.2.zip";
    private String stardistOutput = "Label Image"; 

    private String thMethod = "";
    public double minVesselVol = 50;
    public double maxVesselVol = 50000;
    public int dilVessel = 2;
   

    
    /**
     * Display a message in the ImageJ console and status bar
     */
    public void print(String log) {
        System.out.println(log);
        IJ.showStatus(log);
    }
    
    
    /**
     * Check that needed modules are installed
     */
    public boolean checkInstalledModules() {
        ClassLoader loader = IJ.getClassLoader();
        try {
            loader.loadClass("net.haesleinhuepf.clij2.CLIJ2");
        } catch (ClassNotFoundException e) {
            IJ.log("CLIJ not installed, please install from update site");
            return false;
        }
        try {
            loader.loadClass("mcib3d.geom.Object3D");
        } catch (ClassNotFoundException e) {
            IJ.log("3D ImageJ Suite not installed, please install from update site");
            return false;
        }
        return true;
    }
    
    
    /**
     * Check that required StarDist models are present in Fiji models folder
     */
    public boolean checkStarDistModels() {
        FilenameFilter filter = (dir, name) -> name.endsWith(".zip");
        File[] modelList = stardistModelsPath.listFiles(filter);
        int index = org.scijava.util.ArrayUtils.indexOf(modelList, new File(stardistModelsPath+File.separator+stardistFociModel));
        if (index == -1) {
            IJ.showMessage("Error", stardistFociModel + " StarDist model not found, please add it in Fiji models folder");
            return false;
        }
        return true;
    }
    
    
    /**
     * Find images extension
     */
    public String findImageType(File imagesFolder) {
        String ext = "";
        String[] files = imagesFolder.list();
        for (String name : files) {
            String fileExt = FilenameUtils.getExtension(name);
            switch (fileExt) {
               case "nd" :
                   ext = fileExt;
                   break;
                case "czi" :
                   ext = fileExt;
                   break;
                case "lif"  :
                    ext = fileExt;
                    break;
                case "ics" :
                    ext = fileExt;
                    break;
                case "ics2" :
                    ext = fileExt;
                    break;
                case "lsm" :
                    ext = fileExt;
                    break;
                case "tif" :
                    ext = fileExt;
                    break;
                case "tiff" :
                    ext = fileExt;
                    break;
            }
        }
        return(ext);
    }
    
    
    /**
     * Find images in folder
     */
    public ArrayList<String> findImages(String imagesFolder, String imageExt) {
        File inDir = new File(imagesFolder);
        String[] files = inDir.list();
        if (files == null) {
            System.out.println("No image found in " + imagesFolder);
            return null;
        }
        ArrayList<String> images = new ArrayList();
        for (String f : files) {
            // Find images with extension
            String fileExt = FilenameUtils.getExtension(f);
            if (fileExt.equals(imageExt) && !f.startsWith("."))
                images.add(imagesFolder + File.separator + f);
        }
        Collections.sort(images);
        return(images);
    }
    
    
    /**
     * Flush and close an image
     */
    public void closeImage(ImagePlus img) {
        img.flush();
        img.close();
    }
    
    
    /**
     * Find images in folder
     */
    public void initResults(BufferedWriter results) throws IOException {
        results.write("Image name\tImage volume-ROI volume (µm3)\tRNA background intensity\tVessels volume (µm3)\tNb RNA dots in vessels\t"
                + "RNA volume in vessels (µm3)\tRNA intensity in vessels\tRNA corrected intensity in vessels\t"
                + "Nb RNA out of vessels\tRNA volume out of vessels (µm3)\tRNA intensity out of vessels\t"
                + "RNA corrected intensity out of vessels\n");
        results.flush();
    }
        
    
    /**
     * Find image calibration
     * @param meta
     * @return 
     */
    public void findImageCalib(IMetadata meta) {
        cal = new Calibration();
        cal.pixelWidth = meta.getPixelsPhysicalSizeX(0).value().doubleValue();
        cal.pixelHeight = cal.pixelWidth;
        if (meta.getPixelsPhysicalSizeZ(0) != null)
            cal.pixelDepth = meta.getPixelsPhysicalSizeZ(0).value().doubleValue();
        else
            cal.pixelDepth = 1;
        cal.setUnit("microns");
        System.out.println("XY calibration = " + cal.pixelWidth + ", Z calibration = " + cal.pixelDepth);
    }
    
    
    /**
     * Find channels name
     * @throws loci.common.services.DependencyException
     * @throws loci.common.services.ServiceException
     * @throws loci.formats.FormatException
     * @throws java.io.IOException
     */
    public String[] findChannels (String imageName, IMetadata meta, ImageProcessorReader reader) throws loci.common.services.DependencyException, ServiceException, FormatException, IOException {
        int chs = reader.getSizeC();
        String[] channels = new String[chs];
        String imageExt =  FilenameUtils.getExtension(imageName);
        switch (imageExt) {
            case "nd" :
                for (int n = 0; n < chs; n++) 
                {
                    if (meta.getChannelID(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelName(0, n);
                }
                break;
            case "nd2" :
                for (int n = 0; n < chs; n++) 
                {
                    if (meta.getChannelID(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelName(0, n);
                }
                break;
            case "lif" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null || meta.getChannelName(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelName(0, n);
                break;
            case "czi" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelFluor(0, n);
                break;
            case "ics" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelExcitationWavelength(0, n).value().toString();
                break;
            case "ics2" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelExcitationWavelength(0, n).value().toString();
                break;   
            default :
                for (int n = 0; n < chs; n++)
                    channels[n] = Integer.toString(n);
        }
        return(channels);         
    }
    
    
    /**
     * Generate dialog box
     */
    public String[] dialog(String[] chs) { 
        GenericDialogPlus gd = new GenericDialogPlus("Parameters");
        gd.setInsets​(0, 120, 0);
        gd.addImage(icon);
        
        gd.addMessage("Channels", Font.getFont("Monospace"), Color.blue);
        int index = 0;
        for (String chNames : channelNames) {
            gd.addChoice(chNames+": ", chs, chs[index]);
            index++;
        }
        
        gd.addMessage("Vessels detection", Font.getFont("Monospace"), Color.blue);
        String[] thMethods = AutoThresholder.getMethods();
        gd.addChoice("Threshold method: ",thMethods, thMethods[11]);
        gd.addNumericField("Min vessel volume (µm3): ", minVesselVol);
        gd.addNumericField("Max vessel volume (µm3): ", maxVesselVol);
        gd.addNumericField("Vessel dilation (µm): ", dilVessel);
        
        gd.addMessage("Image calibration", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("XY pixel size (µm): ", cal.pixelWidth);
        gd.addNumericField("Z pixel size (µm): ", cal.pixelDepth);
        gd.showDialog();
        
        String[] chChoices = new String[channelNames.length];
        for (int n = 0; n < chChoices.length; n++) 
            chChoices[n] = gd.getNextChoice();
       
        thMethod = gd.getNextChoice();
        minVesselVol = gd.getNextNumber();
        maxVesselVol = gd.getNextNumber();
        dilVessel = (int)gd.getNextNumber();
        
        cal.pixelWidth = cal.pixelHeight = gd.getNextNumber();
        cal.pixelDepth = gd.getNextNumber();
        pixVol = cal.pixelWidth*cal.pixelWidth*cal.pixelDepth;
        
        if (gd.wasCanceled())
            chChoices = null; 
                
        return(chChoices);
    }
    
    
    public  Objects3DIntPopulation vesselsDetection(ImagePlus imgVessel, ArrayList<Roi> rois) {
        System.out.println("Finding vessels...");
        ImagePlus imgLOG = new Duplicator().run(imgVessel);
        IJ.run(imgLOG, "Laplacian of Gaussian", "sigma=20 scale_normalised negate stack");
        ImagePlus imgTh = threshold(imgLOG, thMethod);
        imgTh.setCalibration(cal); 
        closeImage(imgLOG);
        
        Objects3DIntPopulation pop;
        if (!rois.isEmpty()) {
            ImagePlus imgFill = fillImg(imgTh, rois);
            imgFill.setCalibration(cal); 
            pop = getPopFromImage(imgFill);
            closeImage(imgFill);
        } else {
            pop = getPopFromImage(imgTh);
        }
        System.out.println("Nb vessels detected = " + pop.getNbObjects());
        closeImage(imgTh);
        
        Objects3DIntPopulation popFilter = new Objects3DIntPopulationComputation(pop).getFilterSize(minVesselVol/pixVol, maxVesselVol/pixVol);
        popFilter.resetLabels();
        System.out.println("Nb vessels remaining after size filtering = "+ popFilter.getNbObjects());
        
        return(popFilter);
    }
    

    /**
     * Threshold using CLIJ2
     */
    public ImagePlus threshold(ImagePlus img, String thMed) {
        ClearCLBuffer imgCL = clij2.push(img);
        ClearCLBuffer imgCLBin = clij2.create(imgCL);
        clij2.automaticThreshold(imgCL, imgCLBin, thMed);
        ImagePlus imgBin = clij2.pull(imgCLBin);
        clij2.release(imgCL);
        clij2.release(imgCLBin);
        return(imgBin);
    }
    
    
    /**
     * Fill ROIs with zero in image
     */
    private ImagePlus fillImg(ImagePlus img, ArrayList<Roi> rois) {
        img.getProcessor().setColor(Color.BLACK);
        for (int s = 1; s <= img.getNSlices(); s++) {
            img.setSlice(s);
            for (Roi r : rois) {
                img.setRoi(r);
                img.getProcessor().fill(img.getRoi());
            }
        }
        img.deleteRoi();
        return(img);
    } 
    
    
    /**
     * Get objects population from an image
     */
    private Objects3DIntPopulation getPopFromImage(ImagePlus img) {
        ImageLabeller labeller = new ImageLabeller();
        ImageInt labels = labeller.getLabels(ImageHandler.wrap(img));
        Objects3DIntPopulation pop = new Objects3DIntPopulation(labels);
        return pop;
    } 
    
    
    /**
     * Dilate all objects in population
     */
    public Objects3DIntPopulation popDilation(Objects3DIntPopulation pop, ImagePlus img) {
        Objects3DIntPopulation dilatePop = new Objects3DIntPopulation();
        for (Object3DInt obj: pop.getObjects3DInt()) {
            Object3DInt dilateObj = dilateObj(img, obj, dilVessel);
            dilateObj.setLabel(obj.getLabel());
            dilatePop.addObject(dilateObj);
        }
        return(dilatePop);
    }
    
    
    /**
     * Return dilated object restricted to image borders 
     */
    public Object3DInt dilateObj(ImagePlus img, Object3DInt obj, double dilSize) {
        Object3DInt objDil = new Object3DComputation(obj).getObjectDilated((float)(dilSize/cal.pixelWidth), 
                (float)(dilSize/cal.pixelHeight), (float)(dilSize/cal.pixelDepth));
        
        // Check if object go outside image
        BoundingBox bbox = objDil.getBoundingBox();
        BoundingBox imgBbox = new BoundingBox(ImageHandler.wrap(img));
        int[] box = {imgBbox.xmin, imgBbox.xmax, imgBbox.ymin, imgBbox.ymax, imgBbox.zmin, imgBbox.zmax};
        if (bbox.xmin < 0 || bbox.xmax > imgBbox.xmax || bbox.ymin < 0 || bbox.ymax > imgBbox.ymax
                || bbox.zmin < 0 || bbox.zmax > imgBbox.zmax) {
            Object3DInt objDilImg = new Object3DInt();
            for (Object3DPlane p : objDil.getObject3DPlanes()) {
                for (VoxelInt v : p.getVoxels()) {
                    if (v.isInsideBoundingBox(box))
                        objDilImg.addVoxel(v);
                }
            }
            return(objDilImg);
        } else {
            return(objDil);
        }
    }
    
    
    /**
     * Apply StarDist 2D slice by slice
     * Label detections in 3D
     * @return objects population
     */
    public Objects3DIntPopulation stardistDetection(ImagePlus imgIn, ArrayList<Roi> rois) throws IOException{
        System.out.println("Finding RNA dots...");
        ImagePlus img = new Duplicator().run(imgIn);
        
        // StarDist
        File starDistModelFile = new File(stardistModelsPath+File.separator+stardistFociModel);
        StarDist2D star = new StarDist2D(syncObject, starDistModelFile);
        star.loadInput(img);
        star.setParams(stardistPercentileBottom, stardistPercentileTop, stardistProbThresh, stardistOverlayThresh, stardistOutput);
        star.run();
        closeImage(img);

        // Label detections in 3D
        ImagePlus imgOut = star.getLabelImagePlus();       
        ImagePlus imgLabels = star.associateLabels(imgOut);
        imgLabels.setCalibration(cal); 
        closeImage(imgOut);
        
        Objects3DIntPopulation pop;
        if (rois != null) {
            ImagePlus imgFill = fillImg(imgLabels, rois);
            imgFill.setCalibration(cal);
            pop = new Objects3DIntPopulation(ImageHandler.wrap(imgFill));
            closeImage(imgFill);
        } else { 
            pop = new Objects3DIntPopulation(ImageHandler.wrap(imgLabels));
        }
       closeImage(imgLabels);
       System.out.println("Nb RNA dots detected = " + pop.getNbObjects());
       return(pop);
    }
    
    
    /**
     * Find RNA dots associated or not to vessels
     */
    public Objects3DIntPopulation findRNAInOutVessels(Objects3DIntPopulation rnaPop, Objects3DIntPopulation vesselsPop, ImagePlus imgRNA, boolean in) {
        ImageHandler imh = ImageHandler.wrap(imgRNA).createSameDimensions();
        rnaPop.drawInImage(imh);
        ImageHandler imhCopy = imh.duplicate();
        
        for (Object3DInt obj : vesselsPop.getObjects3DInt()) {
            obj.drawObject(imh, 0);
        }
       
        Objects3DIntPopulation pop;
        if (!in) {
            pop = new Objects3DIntPopulation(imh);
            System.out.println(pop.getNbObjects() + " RNA dots found out of vessels");
        } else {
            ImageCalculator imgCal = new ImageCalculator();
            ImagePlus imgSub = imgCal.run("subtract stack create", imhCopy.getImagePlus(), imh.getImagePlus());
            ImageHandler imhSub = ImageHandler.wrap(imgSub);
            pop = new Objects3DIntPopulation(imhSub);
            System.out.println(pop.getNbObjects() + " RNA dots found in vessels");
        }
        imh.closeImagePlus();
        imhCopy.closeImagePlus();
        return(pop);  
    }
    
    
    /**
     * Draw results in images
     */
    public void drawResults(ImagePlus imgRNA, Objects3DIntPopulation vesselsPop, Objects3DIntPopulation rnaIn, 
            Objects3DIntPopulation rnaOut,String outDirResults, String rootName) {

        ImageHandler imgVessels = ImageHandler.wrap(imgRNA).createSameDimensions();
        ImageHandler imgRNAIn = ImageHandler.wrap(imgRNA).createSameDimensions();
        ImageHandler imgRNAOut = ImageHandler.wrap(imgRNA).createSameDimensions();
        
        // Draw vessels population in blue
        for (Object3DInt obj: vesselsPop.getObjects3DInt()) 
                obj.drawObject(imgVessels, 255);
        // Draw RNA dots in vessels in green
        for (Object3DInt obj: rnaIn.getObjects3DInt()) 
                obj.drawObject(imgRNAIn, 255);
        // Draw RNA dots out of vessels in red
        for (Object3DInt obj: rnaOut.getObjects3DInt()) 
                obj.drawObject(imgRNAOut, 255);
        ImagePlus[] imgColors = {imgRNAOut.getImagePlus(), imgRNAIn.getImagePlus(), imgVessels.getImagePlus()};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(cal);
        IJ.run(imgObjects, "Enhance Contrast", "saturated=0.35");

        // Save images
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(outDirResults + rootName + "_results.tif");
        imgVessels.closeImagePlus();
        imgRNAIn.closeImagePlus();
        imgRNAOut.closeImagePlus();
    }
    
    
    public void writeResults(Objects3DIntPopulation vesselsPop, Objects3DIntPopulation rnaInVessel, 
            Objects3DIntPopulation rnaOutVessel, ImagePlus imgRNA, ArrayList<Roi> rois, String imgName, BufferedWriter results) throws IOException {
       
       double imgVol = imgRNA.getWidth()*imgRNA.getHeight()*imgRNA.getNSlices()*pixVol - getRoisVolume(rois, imgRNA);
       double bgRNA = findBackground(imgRNA);
       double vesselsVol = findPopVolume(vesselsPop);
       
       int rnaInNb = rnaInVessel.getNbObjects();
       double rnaInVol = findPopVolume(rnaInVessel);
       double rnaInInt = findPopIntensity(rnaInVessel, imgRNA);
       double rnaInIntBgCor = rnaInInt - bgRNA*rnaInVol/pixVol;
       
       int rnaOutNb = rnaOutVessel.getNbObjects();
       double rnaOutVol = findPopVolume(rnaOutVessel);
       double rnaOutInt = findPopIntensity(rnaOutVessel, imgRNA);
       double rnaOutIntBgCor = rnaOutInt - bgRNA*rnaInVol/pixVol;
       
       results.write(imgName+"\t"+imgVol+"\t"+bgRNA+"\t"+vesselsVol+"\t"+rnaInNb+"\t"+rnaInVol+"\t"+rnaInInt+"\t"+rnaInIntBgCor+"\t"+
               rnaOutNb+"\t"+rnaOutVol+"\t"+rnaOutInt+"\t"+rnaOutIntBgCor+"\n");
       results.flush();
    }
    
    
    /**
     * Compute ROI volume
     */
    public double getRoisVolume(ArrayList<Roi> rois, ImagePlus img) {
        double area = 0;
        img.setCalibration(cal);
        for(Roi roi: rois) {
            PolygonRoi poly = new PolygonRoi(roi.getFloatPolygon(), Roi.FREEROI);
            poly.setLocation(0, 0);
            img.setRoi(poly);

            ResultsTable rt = new ResultsTable();
            Analyzer analyzer = new Analyzer(img, Analyzer.AREA, rt);
            analyzer.measure();
            area += rt.getValue("Area", 0);
        }
        return(area * img.getNSlices() * cal.pixelDepth);
    }   
    
    
    /**
     * Find image background intensity:
     * z-project over min intensity + read median intensity
     */
    public double findBackground(ImagePlus img) {
      ImagePlus imgProj = doZProjection(img, ZProjector.MIN_METHOD);
      double bg = imgProj.getProcessor().getStatistics().median;
      System.out.println("Background (median of the min projection) = " + bg);
      closeImage(imgProj);
      return(bg);
    }
       
    
    /**
     * Do Z-projection
     */
    public ImagePlus doZProjection(ImagePlus img, int param) {
        ZProjector zproject = new ZProjector();
        zproject.setMethod(param);
        zproject.setStartSlice(1);
        zproject.setStopSlice(img.getNSlices());
        zproject.setImage(img);
        zproject.doProjection();
       return(zproject.getProjection());
    }
    
    /**
     * Find total volume of all objects in population  
     */
    public double findPopVolume(Objects3DIntPopulation pop) {
        double sumVol = 0;
        for(Object3DInt obj : pop.getObjects3DInt()) {
            MeasureVolume volMes = new MeasureVolume(obj);
            sumVol +=  volMes.getValueMeasurement(MeasureVolume.VOLUME_UNIT);
        }
        return(sumVol);
    }
    
    /**
     * Find total intensity of all objects in population
     */
    public double findPopIntensity(Objects3DIntPopulation pop, ImagePlus img) {
        double sumInt = 0;
        ImageHandler imh = ImageHandler.wrap(img);
        for(Object3DInt obj : pop.getObjects3DInt()) {
            MeasureIntensity intMes = new MeasureIntensity(obj, imh);
            sumInt +=  intMes.getValueMeasurement(MeasureIntensity.INTENSITY_SUM);
        }
        return(sumInt);
    }
    
}
