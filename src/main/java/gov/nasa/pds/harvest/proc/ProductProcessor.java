package gov.nasa.pds.harvest.proc;

import java.io.File;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;

import gov.nasa.pds.harvest.cfg.Configuration;
import gov.nasa.pds.harvest.job.Job;
import gov.nasa.pds.harvest.meta.Metadata;
import gov.nasa.pds.harvest.meta.ex.AutogenExtractor;
import gov.nasa.pds.harvest.meta.ex.BasicMetadataExtractor;
import gov.nasa.pds.harvest.meta.ex.BundleMetadataExtractor;
import gov.nasa.pds.harvest.meta.ex.CollectionMetadataExtractor;
import gov.nasa.pds.harvest.meta.ex.FileMetadataExtractor;
import gov.nasa.pds.harvest.meta.ex.InternalReferenceExtractor;
import gov.nasa.pds.harvest.meta.ex.SearchMetadataExtractor;
import gov.nasa.pds.harvest.util.out.RegistryDocWriter;
import gov.nasa.pds.harvest.util.out.WriterManager;
import gov.nasa.pds.harvest.util.xml.XmlDomUtils;


public class ProductProcessor
{
    private Logger log;
    
    // Skip files bigger than 10MB
    private static final long MAX_XML_FILE_LENGTH = 10_000_000;

    private Configuration config;
    private DocumentBuilderFactory dbf;

    // Bundle and Collection extractors & processors
    private BundleMetadataExtractor bundleExtractor;
    private CollectionMetadataExtractor collectionExtractor;
    private CollectionInventoryProcessor invProc;
    
    // Common extractors
    private BasicMetadataExtractor basicExtractor;
    private InternalReferenceExtractor refExtractor;
    private AutogenExtractor autogenExtractor;
    private SearchMetadataExtractor searchExtractor;
    private FileMetadataExtractor fileDataExtractor;
    
    
    public ProductProcessor()
    {
    }
    
    /**
     * Process one file
     * @param file PDS label XML file
     * @param job Harvest job configuration parameters
     * @throws Exception Generic exception
     */
    private void processFile(File file, Job job) throws Exception
    {
        // Skip very large files
        if(file.length() > MAX_XML_FILE_LENGTH)
        {
            log.warn("File is too big to parse: " + file.getAbsolutePath());
            return;
        }

        Document doc = XmlDomUtils.readXml(dbf, file);
        processMetadata(file, doc, job);
    }

    
    /**
     * Extract metadata from a label file
     * @param file PDS label file
     * @param doc Parsed XML DOM model of the PDS label file
     * @param job Harvest job configuration parameters
     * @throws Exception Generic exception
     */
    private void processMetadata(File file, Document doc, Job job) throws Exception
    {
        // Extract basic metadata
        Metadata meta = basicExtractor.extract(file, doc, job);

        log.info("Processing " + file.getAbsolutePath());

        String rootElement = doc.getDocumentElement().getNodeName();

        // Process Collection specific data
        if("Product_Collection".equals(rootElement))
        {
            processInventoryFiles(file, doc, meta, job.jobId);
        }
        // Process Bundle specific data
        else if("Product_Bundle".equals(rootElement))
        {
            addCollectionRefs(meta, doc);
        }
        // Process supplemental products
        else if("Product_Metadata_Supplemental".equals(rootElement))
        {
            //SupplementalWriter swriter = WriterManager.getInstance().getSupplementalWriter();
            //swriter.write(file);
        }
        
        // Internal references
        refExtractor.addRefs(meta.intRefs, doc);
        
        // Extract fields autogenerated from data dictionary
        autogenExtractor.extract(file, meta.fields, job);
        
        // Extract search fields
        searchExtractor.extract(doc, meta.fields);

        // Extract file data
        fileDataExtractor.extract(file, meta, job);
        
        RegistryDocWriter writer = WriterManager.getInstance().getRegistryWriter();
        writer.write(meta, job.jobId);
    }

    
    /**
     * Process collection inventory files
     * @param collectionFile PDS4 collection label file
     * @param doc Parsed PDS4 collection label file.
     * @param meta Collection metadata extracted from PDS4 collection label file
     * @param jobId Harvest job id
     * @throws Exception Generic exception
     */
    private void processInventoryFiles(File collectionFile, Document doc, Metadata meta, String jobId) throws Exception
    {
        Set<String> fileNames = collectionExtractor.extractInventoryFileNames(doc);
        if(fileNames == null) return;
        
        for(String fileName: fileNames)
        {
            File invFile = new File(collectionFile.getParentFile(), fileName);
            invProc.writeCollectionInventory(meta, invFile, jobId);
        }
    }

    
    private void addCollectionRefs(Metadata meta, Document doc) throws Exception
    {
        List<BundleMetadataExtractor.BundleMemberEntry> bmes = bundleExtractor.extractBundleMemberEntries(doc);

        for(BundleMetadataExtractor.BundleMemberEntry bme: bmes)
        {
            bundleExtractor.addRefs(meta.intRefs, bme);
        }
    }

}
