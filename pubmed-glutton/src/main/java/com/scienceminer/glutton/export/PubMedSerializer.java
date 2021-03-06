package com.scienceminer.glutton.export;

import java.io.*;
import java.util.*;

import com.opencsv.*; 

import com.scienceminer.glutton.utilities.GluttonConfig;
import com.scienceminer.glutton.data.Biblio;
import com.scienceminer.glutton.data.ClassificationClass;
import com.scienceminer.glutton.data.MeSHClass;
import com.scienceminer.glutton.data.BiblioDefinitions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.io.*;

/**
 * Class for serializing PubMed biblio objects in different formats. 
 */

public class PubMedSerializer {

    // csv 
    public static String[] CSV_HEADERS = { "pmid", "doi", "pmc", "title", "abstract", "MeSH Terms", "publication year", 
        "authors", "keywords", "publisher", "host", "affiliation", "author countries", "grid", "funding_organization", 
        "funding country" };

    public static void serializeCsv(CSVWriter writer, Biblio biblio) throws IOException {
        StringBuilder meshTerms = new StringBuilder();
        if (biblio.getClassifications() != null) {
            boolean first = true;
            for (ClassificationClass theClass : biblio.getClassifications()) {
                if (theClass.getScheme().equals("MeSH")) {
                    if (first)
                        first = false;
                    else
                        meshTerms.append(", ");
                    meshTerms.append(((MeSHClass)theClass).getDescriptorName());
                }
            }
        }

        String host = null;
        
        if (biblio.getHostType() == BiblioDefinitions.JOURNAL)
            host = biblio.getTitle();
        else if (biblio.getHostType() == BiblioDefinitions.PROCEEDINGS)
            host = biblio.getTitle();
        else if (biblio.getHostType() == BiblioDefinitions.COLLECTION)
            host = biblio.getCollectionTitle();
        else if (biblio.getTitle() != null)
            host = biblio.getTitle();
        else if (biblio.getHostType() == BiblioDefinitions.UNKNOWN)
            host = "";
        else
            host = "";

        // cleaning a bit some text noise from pubmed
        String articleTitle = biblio.getArticleTitle();
        if (articleTitle != null) {
            if (articleTitle.endsWith(".")) {
                articleTitle = articleTitle.substring(0,articleTitle.length()-1);
            }
            if (articleTitle.startsWith("[")) {
                articleTitle = articleTitle.substring(1,articleTitle.length());
            }
            if (articleTitle.endsWith("]")) {
                articleTitle = articleTitle.substring(0,articleTitle.length()-1);
            }
        }

        String[] data1 = { ""+biblio.getPmid(), biblio.getDoi(), biblio.getPmc(), articleTitle, biblio.getAbstract(), 
        meshTerms.toString(), Biblio.dateISODisplayFormat(biblio.getPublicationDate()), biblio.printAuthorList(), biblio.getKeywords(),
        biblio.getPublisher(), host, "", "", "", "", "" };
        writer.writeNext(data1);
    }

    public static void serializeJson(Writer writer, Biblio biblio) {
        
    }

    public static void serializeTei(Writer writer, Biblio biblio) {
        
    }


}