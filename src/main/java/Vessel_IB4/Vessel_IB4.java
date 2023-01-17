package Vessel_IB4;


import Vessel_IB4_Utils.Vessel_Processing;
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
import mcib3d.geom2.Objects3DIntPopulation;


/**
 *
 * @author phm
 */
public class Vessel_IB4 implements PlugIn {
    
    private Vessel_IB4_Utils.Vessel_Processing process = new Vessel_Processing();
    
    @Override
    public void run(String arg) {
            try {
                String imageDir = IJ.getDirectory("Choose directory containing images and rois ...");
                if (imageDir == null) {
                    return;
                }
                // Find images with extension
                String file_ext = "nd";
                ArrayList<String> imageFiles = process.findImages(imageDir, file_ext);

                if (imageFiles == null) {
                    System.out.println("No Image found in "+imageDir);
                    return;
                }                                                           
                // create output folder
                String outDirResults = imageDir + File.separator+ "Results"+ File.separator;
                File outDir = new File(outDirResults);
                if (!Files.exists(Paths.get(outDirResults))) {
                    outDir.mkdir();
                }
               
                // Create OME-XML metadata store of the latest schema version
                ServiceFactory factory;
                factory = new ServiceFactory();
                OMEXMLService service = factory.getInstance(OMEXMLService.class);
                IMetadata meta = service.createOMEXMLMetadata();
                ImageProcessorReader reader = new ImageProcessorReader();
                reader.setMetadataStore(meta);
                reader.setId(imageFiles.get(0));
                String[] channels = process.findChannels(imageFiles.get(0), meta, reader);
                process.cal = process.findImageCalib(meta);

                // Dialog box
                String[] chs = process.dialog(channels);
                if (chs == null) {
                    IJ.showMessage("Error", "Plugin canceled");
                    return;
                }
                
                 // initialize results files
                FileWriter  fwAnalyze_detail = new FileWriter(outDirResults +"results.xls",false);
                BufferedWriter results = new BufferedWriter(fwAnalyze_detail);
                process.InitResults(results);
                
                // Image calibration
                process.findImageCalib(meta);
                for (String f : imageFiles) {
                    String rootName = FilenameUtils.getBaseName(f);
                    reader.setId(f);
                    reader.setSeries(0);
                    ImporterOptions options = new ImporterOptions();
                    options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
                    options.setId(f);
                    options.setSplitChannels(true);
                    if (process.removeSlice != 0) {
                        options.setZBegin(0, process.removeSlice);
                        if (2 * process.removeSlice < reader.getSizeZ())
                            options.setZEnd(0, reader.getSizeZ() - 1  - process.removeSlice);
                        options.setZStep(0, 1);
                    }

                    options.setQuiet(true);

                    // Check if rois file exist, keep rois to clear regions containing "artefacts"
                    ArrayList<Roi> rois = new ArrayList<>();
                    String roiFile = imageDir + File.separator+rootName + ".zip";
                    RoiManager rm = RoiManager.getInstance();
                    if (!new File(roiFile).exists()) {
                        roiFile = imageDir + File.separator+rootName + ".roi";
                        if (new File(roiFile).exists())
                        
                        // Find rois
                        
                        if (rm != null)
                            rm.reset();
                        else
                            rm = new RoiManager(false);
                        rm.runCommand("Open", roiFile);
                        Collections.addAll(rois, rm.getRoisAsArray());
                    }


                    /*
                    * Open Vessel channel
                    */
                    int indexCh = ArrayUtils.indexOf(channels, chs[0]);
                    System.out.println("-- Opening Vessel channel : "+ chs[0]);
                    ImagePlus imgVessel = BF.openImagePlus(options)[indexCh];

                    // Open vessel channel

                    Objects3DIntPopulation vesselsPop = process.findVessel(imgVessel, rois);
                    process.closeImages(imgVessel);

                    /*
                    * Open Channel 1 (gene X)
                    */
                    indexCh = ArrayUtils.indexOf(channels, chs[1]);
                    System.out.println("-- Opening gene X channel : " + chs[1]);
                    ImagePlus imgGeneX = BF.openImagePlus(options)[indexCh];

                    //Find gene X dots
                    Objects3DIntPopulation geneXPop = process.stardistPop(imgGeneX, rois);
                    System.out.println("Finding gene "+geneXPop.getNbObjects()+" X dots");

                    // Find gene dots out to Vessels
                    Objects3DIntPopulation geneOutVessel = process.findGeneIn_OutVessel(geneXPop, vesselsPop, imgGeneX, false);
                    System.out.println(geneOutVessel.getNbObjects() + " geneX found out of Vessels");

                    // Find gene dots into Vessels
                    Objects3DIntPopulation geneInVessel = process.findGeneIn_OutVessel(geneXPop, vesselsPop, imgGeneX, true);
                    System.out.println(geneInVessel.getNbObjects() + " geneX found in Vessels");

                    // Save labelled vessel
                    process.saveCellsLabelledImage(imgGeneX, vesselsPop, geneInVessel, geneOutVessel, outDirResults, rootName);

                    // Find cells parameters in geneX images
                    process.writeResults(vesselsPop, geneInVessel, geneOutVessel, imgGeneX, rootName, results);
                    process.closeImages(imgGeneX);
                }
                if (new File(outDirResults + "results.xls").exists())
                    results.close();
            } catch (IOException | DependencyException | ServiceException | FormatException ex) {
                    Logger.getLogger(Vessel_IB4.class.getName()).log(Level.SEVERE, null, ex);
            }

            IJ.showStatus("Process done ...");
        }
    
    
}
