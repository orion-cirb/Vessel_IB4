package Vessel_IB4;


import Vessel_IB4_Tools.Tools;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.util.ImageProcessorReader;
import loci.plugins.BF;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import loci.plugins.in.ImporterOptions;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;
import java.util.Collections;
import java.util.List;
import mcib3d.geom2.Objects3DIntPopulation;


/**
 * Detect vessels and ARN dots
 * Determine ARN dots volume in/out vessels
 * @author ORION-CIRB
 */
public class Vessel_IB4 implements PlugIn {
    
    private Vessel_IB4_Tools.Tools tools = new Tools();
   
    public void run(String arg) {
            try {
                if (!tools.checkInstalledModules()) {
                    return;
                } 
                
                String imageDir = IJ.getDirectory("Choose directory containing image files...");
                if (imageDir == null) {
                    return;
                }
                
                // Find images with extension
                String fileExt = tools.findImageType(new File(imageDir));
                ArrayList<String> imageFiles = tools.findImages(imageDir, fileExt);
                if (imageFiles == null) {
                    IJ.showMessage("Error", "No images found with " + fileExt + " extension");
                    return;
                }                       
               
                // Create OME-XML metadata store of the latest schema version
                ServiceFactory factory;
                factory = new ServiceFactory();
                OMEXMLService service = factory.getInstance(OMEXMLService.class);
                IMetadata meta = service.createOMEXMLMetadata();
                ImageProcessorReader reader = new ImageProcessorReader();
                reader.setMetadataStore(meta);
                reader.setId(imageFiles.get(0));
                
                // Find image calibration
                tools.findImageCalib(meta);

                // Find channel names
                String[] channels = tools.findChannels(imageFiles.get(0), meta, reader);
                
                // Dialog box
                String[] chs = tools.dialog(channels);
                if (chs == null) {
                    IJ.showStatus("Plugin canceled");
                    return;
                }
                
                // Create output folder
                String outDirResults = imageDir + File.separator+ "Results"+ File.separator;
                File outDir = new File(outDirResults);
                if (!Files.exists(Paths.get(outDirResults))) {
                    outDir.mkdir();
                }
                
                // Write header in results file
                FileWriter fwResults = new FileWriter(outDirResults +"results.xls",false);
                BufferedWriter results = new BufferedWriter(fwResults);
                results.write("Image name\tImage vol (µm3)\tImage-ROI vol (µm3)\tVessels vol (µm3)\tGeneX channel bg\tNb GeneX dots in vessels\tGeneX dots vol in vessels (µm3)\t"
                        + "GeneX dots int in vessels\tGeneX dots bg corr int in vessels\tNb GeneX dots out vessels\tGeneX dots vol out vessels (µm3)\tGeneX dots int out vessels"
                        + "\tGeneX dots bg corr int out vessels\n");
                results.flush();
                
                
                for (String f: imageFiles) {
                    String rootName = FilenameUtils.getBaseName(f);
                    tools.print("--- ANALYZING IMAGE " + rootName + " ------");
                    reader.setId(f);
                    
                    ImporterOptions options = new ImporterOptions();
                    options.setId(f);
                    options.setSplitChannels(true);
                    options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
                    options.setQuiet(true);

                    // Check if rois file exist, keep rois to clear regions containing "artefacts"
                    ArrayList<Roi> rois = new ArrayList<>();
                    String roiRootName = imageDir + File.separator + rootName; 
                    String roiName = new File(roiRootName + ".zip").exists() ? roiRootName + ".zip" : roiRootName + ".roi";
                    if (new File(roiName).exists()) {
                        RoiManager rm = new RoiManager(false);
                        if (rm != null)
                            rm.reset();
                        else
                            rm = new RoiManager(false);
                        rm.runCommand("Open", roiName);
                        Collections.addAll(rois, rm.getRoisAsArray());
                    }

                    // Open vessels channel
                    tools.print("- Analyzing vessels channel -");
                    int indexCh = ArrayUtils.indexOf(channels, chs[0]);
                    ImagePlus imgVessel = BF.openImagePlus(options)[indexCh];
                    Objects3DIntPopulation vesselsPop = tools.findVessels(imgVessel, rois);

                    // Open geneX channel
                    tools.print("- Analyzing geneX channel -");
                    indexCh = ArrayUtils.indexOf(channels, chs[1]);
                    ImagePlus imgGeneX = BF.openImagePlus(options)[indexCh];
                    Objects3DIntPopulation geneXPop = tools.findGenes(imgGeneX, rois);
                    
                    // Find geneX dots into and out of vessels
                    List<Objects3DIntPopulation> geneXInOutPops = tools.findGeneXInOutVessels(geneXPop, vesselsPop, imgGeneX);
                    Objects3DIntPopulation geneXInPop = geneXInOutPops.get(0);
                    System.out.println(geneXInPop.getNbObjects() + " geneX dots found into vessels");
                    Objects3DIntPopulation geneXOutPop = geneXInOutPops.get(1);
                    System.out.println(geneXOutPop.getNbObjects() + " geneX dots found out of vessels");

                    // Draw results
                    tools.print("- Drawing and writing results -");
                    tools.drawResults(imgGeneX, vesselsPop, geneXInPop, geneXOutPop, outDirResults, rootName);

                    // Write results
                    tools.writeResults(results, vesselsPop, geneXInPop, geneXOutPop, imgGeneX, rois, rootName);
                    
                    tools.flushCloseImg(imgVessel);
                    tools.flushCloseImg(imgGeneX);
                }
                results.close();
            } catch (IOException | DependencyException | ServiceException | FormatException ex) {
                    Logger.getLogger(Vessel_IB4.class.getName()).log(Level.SEVERE, null, ex);
            }

            tools.print("All done!");
        }
}
