package com.idega.block.websearch.business;



import java.net.HttpURLConnection;

import com.lucene.index.IndexWriter;
import com.lucene.document.Field;
import com.lucene.document.DateField;
import com.idega.block.websearch.data.*;

/**
 * <p><code>Crawler</code> Web crawler.</p>
* This class is a part of the websearch webcrawler and search engine block. <br>
* It is based on the <a href="http://lucene.apache.org">Lucene</a> java search engine from the Apache group and loosly <br>
* from the work of David Duddleston of i2a.com.<br>
*
* @copyright Idega Software 2002
* @author <a href="mailto:eiki@idega.is">Eirikur Hrafnsson</a>
 */

public final class Crawler {
    
    private WebSearchIndex index;
    private com.lucene.index.IndexReader reader;
    private IndexWriter writer;
    
    private java.util.Stack linkQueue;
    private java.util.TreeSet links;
    private ContentHandler handler;
    
    private String rootURL;
    private String seedURL[];
    private String scopeURL[];
    private String indexPath; // search index path
    private boolean created; // if search index has been created
    private String cookie;
    
    // reporting
    private int reporting; // reporting level
    private java.io.PrintWriter report;
    
    // Current URL data
    private java.net.URL currentURL;
    private String currentURLPath; // path of URL (no file)
    // private String href;
    private String contentType;
    private long lastModified;
    
    private ContentHandler htmlHandler = new HTMLHandler();
    private ContentHandler pdfHandler = new PDFHandler();
    
    
    
    
    /**
     *
     */
    private Crawler() {
    }
    /**
     *
     */
    public Crawler(WebSearchIndex index) {
        this(index, 0);
    }
    /**
     *
     */
    public Crawler(WebSearchIndex index, int reporting) {
        this(index, reporting, null);
    }
    
    public Crawler(WebSearchIndex index, int reporting, java.io.PrintWriter report) {
        try {
            
            this.index = index;
            this.seedURL = index.getSeed();
            this.scopeURL = index.getScope();
            this.indexPath = index.getIndexPath();
            this.rootURL = seedURL[0].substring(0, seedURL[0].indexOf("/", 8));
            
            this.created = false;
            
            this.reporting = reporting;
            if (reporting > 0) {
                if (report == null) {
                    this.report = new java.io.PrintWriter(new java.io.StringWriter());
                }
                    this.report = report;
            }
            
            linkQueue = new java.util.Stack();
            links = new java.util.TreeSet();
            for (int i = 0; i < seedURL.length; i++) {
                links.add(seedURL[i].toLowerCase());
                linkQueue.push(seedURL[i]);
            }
            
            
        } catch (Exception e) {
            e.printStackTrace();
        };
    }
    public void crawl() {
        try {
            
            if (reporting > 0) report.println("START CRAWLING");
            
            java.io.File file = new java.io.File(indexPath);
            
            if (!file.exists()) {
                // need code here to build directory structure if needed.
                if (reporting > 0) report.println("create new index");
                IndexWriter writer = new IndexWriter(indexPath, new com.lucene.analysis.StopAnalyzer(), true);
                writer.close();
            } else {
                // delete all files for now and build new index.
                // implement incremental index later.
                
                //delete all
                if (reporting > 0) report.println("index exists, delete all files");
                com.lucene.index.IndexReader reader = com.lucene.index.IndexReader.open(indexPath);
                int count = reader.numDocs();
                if (reporting > 0) {
                    report.println("deleting " + count + " records");
                    report.flush();
                }
                for (int i = 0; i < count; i++) {
                    //if (reporting > 1) report.println("deleted " + i);
                    reader.delete(i);
                }
                reader.close();
            }
            
            
            
            // create new IndexWriter
            writer = new IndexWriter(indexPath, new com.lucene.analysis.StopAnalyzer(), false);
            
            String url;
            //System.out.println(linkQueue.toString());
            
            while (!linkQueue.empty()) {
                url = (String)linkQueue.pop();

                if (!url.startsWith(rootURL)) {
                    // root has changed.  
                    // example http://www.12a.com to https://secure.i2a.com/
                    this.rootURL = url.substring(0, url.indexOf("/", 8));
                }
                if (reporting > 1) report.println();
                if (reporting > 1) {
                    report.print("SCANNING : " + url);
                    report.flush();
                }
                
                String result = scanPage(url);

                if (result.equals("good")) {
                    if (reporting > 1) report.print(" status: " + result);
                    if (reporting > 2) {
                        report.println(" lastModified : " + lastModified);
                        report.println(" contentType : " + contentType);
                        report.println(" robot rules: index=" + handler.getRobotIndex()
                        + "  follow=" + handler.getRobotFollow());
                        report.println(" HREF : " +handler.getHREF());
                        report.println(" title : " +handler.getTitle());
                        report.println(" author : " +handler.getAuthor());
                        report.println(" published : " +handler.getPublished());
                        report.println(" description : " +handler.getDescription());
                        report.println(" keywords : " +handler.getKeywords());
                        report.println(" links : " + handler.getLinks());
                        if (reporting > 3) {
                            report.println(" contents : " +handler.getContents());
                        }
                    }
                    
                } else {
                    if (reporting == 1) {
                        report.println();
                        report.println("SCANNED : " + url);
                    }
                    if (reporting > 0) report.println(" *status: " + result);
                }
                if (reporting > 0) report.flush();
            }
            if (reporting > 0) {
                report.println();
                report.println();
                report.println("DONE CRAWLING");
                report.println("links crawled");
                java.util.Iterator it = links.iterator();
                while (it.hasNext()) {
                    report.println(it.next());
                }
                report.println();
            }
            writer.optimize();
            writer.close();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public final void handleHTML(HttpURLConnection httpCon) throws Exception {
        
        handler = htmlHandler;
        handler.parse(httpCon.getInputStream());
        
        if (handler.getRobotFollow()) {
            java.util.List links = handler.getLinks();
            //System.out.println("link count : " + links.size());
            for (int i = 0; i < links.size(); i++) {
                handleLink((String)links.get(i));
            }
        }
        
        if (handler.getRobotIndex()) indexLucene();
        
    }
    
    
    public void handleLink(String url) {
        
        // Evaluatins to be done in lower case.
        String urlLowCase = url.toLowerCase();
        
        if (!(urlLowCase.startsWith("http://") || 
            urlLowCase.startsWith("https://"))) {
            // link needs to be evaluated, parsed and completed
            url = parseHREF(url, urlLowCase);
            if (url != null) {
                urlLowCase = url.toLowerCase();
            }
        // is full URL
        } 
        if (url != null && inScope(urlLowCase)) {
            // is full URL and in scope.
            if (!links.contains(urlLowCase)) {
                links.add(urlLowCase);
                linkQueue.push(url);
            }
        }
        
    }
     
    
    
    public final void handlePDF(HttpURLConnection httpCon) throws Exception {
        
        handler = pdfHandler;
        handler.parse(httpCon.getInputStream());
        
        indexLucene();
        
    }
/*
 * Index the html in lucene.
 * Path is the url path, and contents is the parsed html
 */
    private void indexLucene() {
        try {
            
            //IndexWriter writer;
            //if (!created) {
            //writer = new IndexWriter(this.indexPath, new StopAnalyzer(), true);
            //created = true;
            //} else {
            //writer = new IndexWriter(this.indexPath, new StopAnalyzer(), false);
            //}
            com.lucene.document.Document mydoc = new com.lucene.document.Document();
            mydoc.add(new Field("uid", currentURL.toString().toLowerCase(), false, true, false));
            mydoc.add(Field.Text("url", currentURL.toString()));
            mydoc.add(Field.Text("contentType", contentType));
            mydoc.add(Field.Keyword("lastModified",
            DateField.timeToString(lastModified)));
            mydoc.add(Field.UnStored("contents", handler.getContents()));
            if (handler.getTitle() != null) {
                mydoc.add(Field.Text("title", handler.getTitle()));
            }
            if (handler.getKeywords() != null) {
                mydoc.add(Field.Text("keywords", handler.getKeywords()));
            }
            if (handler.getDescription() != null) {
                mydoc.add(Field.Text("description", handler.getDescription()));
            }
            if (handler.getCategories() != null) {
                mydoc.add(Field.Text("categories", handler.getCategories()));
            }
            if (handler.getPublished() != -1) {
                // use meta tag
                mydoc.add(Field.Keyword("published",
                DateField.timeToString(handler.getPublished())));
            } else {
                // use lastmodified from http header.
                mydoc.add(Field.Keyword("published",
                DateField.timeToString(lastModified)));
            }
            if (handler.getPublished() != -1) {
                // use meta tag
                mydoc.add(Field.Keyword("published",
                DateField.timeToString(handler.getPublished())));
            } else {
                // use lastmodified from http header.
                
            }
            if (handler.getHREF() != null) {
                // Replace $link with url.
                String href = handler.getHREF();
                int pos = href.indexOf("$link");
                href = href.substring(0, pos) + currentURL.toString()
                + href.substring(pos + 5, href.length());
                mydoc.add(Field.UnIndexed("href", href));
            }
            writer.addDocument(mydoc);
            //writer.close();
        } catch (Exception e) {
            System.out.println(e.toString());
            e.printStackTrace();
        }
    }
    public boolean inScope(String url) {
        
        for (int i = 0; i < scopeURL.length; i++) {
            if (url.startsWith(scopeURL[i])) {
                // in scope
                return true;
            }
        }
        // not in scope
        return false;
        
    }
    public String parseHREF(String url, String urlLowCase) {
        
        // Looks for incomplete URL and completes them
        if (urlLowCase.startsWith("/")) {
            url = rootURL + url;
        } else if (urlLowCase.startsWith("./")) {
            url = currentURLPath + url.substring(1, url.length());
        } else if (urlLowCase.startsWith("../")) {
            int back = 1;
            while (urlLowCase.indexOf("../", back*3) != -1) back++;
            int pos = currentURLPath.length();
            int count = back;
            while (count-- > 0) {
                pos = currentURLPath.lastIndexOf("/", pos) - 1;
            }
            url = currentURLPath.substring(0, pos+2) + url.substring(3*back, url.length());
        } else if (urlLowCase.startsWith("javascript:")) {
            // handle javascript:...
            url = parseJavaScript(url, urlLowCase);
        } else if (urlLowCase.startsWith("#")) {
            // internal anchor... ignore.
            url = null;
        } else if (urlLowCase.startsWith("mailto:")) {
            // handle mailto:...
            url = null;
        } else {
            url = currentURLPath + "/" + url;
        }
        
        // strip anchor if exists otherwise crawler may index content multiple times
        // links to the same url but with unique anchors would be considered unique
        // by the crawler when they should not be
        //int i;
        if (url != null) {
            int i;
            if ((i = url.indexOf("#")) != -1) {
                url = url.substring(0,i);
            }
        }
        return url;
        
    }
    public String parseJavaScript(String url, String urlLowCase) {
        
        if (urlLowCase.startsWith("pop", 11)) {
            int start = urlLowCase.indexOf("'", 13);
            if (start != -1 ) {
                int end = urlLowCase.indexOf("'", start + 1);
                if (end != -1) {
                    url = url.substring(start + 1, end);
                    return parseHREF(url, url.toLowerCase());
                }
            }
            return null;
        } else {
            return null;
        }
        
    }
    public String scanPage(String urlString) {
        
        String status = "good";
        try {
            currentURL = new java.net.URL(urlString);
            currentURLPath = urlString.substring(0, urlString.lastIndexOf("/"));
            HttpURLConnection httpCon = (HttpURLConnection)currentURL.openConnection();
            
            httpCon.setRequestProperty("User-Agent", "i2a Web Search Engine Crawler");
            
            if (cookie != null) {
                httpCon.setRequestProperty("Cookie", this.cookie);
            }
            httpCon.connect();
            
            
            lastModified = httpCon.getLastModified();
            
            if (httpCon.getHeaderField("Set-Cookie") != null) {
                cookie = stripCookie(httpCon.getHeaderField("Set-Cookie"));
                if (reporting > 1) report.print(" got cookie : " + cookie);
            }
            
            if (httpCon.getResponseCode() == HttpURLConnection.HTTP_OK) {
                contentType = httpCon.getContentType();
                if (contentType.indexOf("text/html") != -1) {
                    handleHTML(httpCon);
                } else if (contentType.indexOf("application/pdf") != -1) {
                    handlePDF(httpCon);
                } else {
                    status = "Not an excepted content type : " + contentType;
                }
            } else {
                status = "bad";
            }
            httpCon.disconnect();
        } catch (java.net.MalformedURLException mue) {
            status = mue.toString();
        } catch (java.net.UnknownHostException uh) {
            status = uh.toString(); // Mark as a bad URL
        } catch (java.io.IOException ioe) {
            status = ioe.toString(); // Mark as a bad URL
        } catch (Exception e) {
            status = e.toString(); // Mark as a bad URL
        }
        
        return status;
    }
    public static String stripCookie(String cookie) {
        
        int loc = cookie.indexOf(";");
        return (loc > 0) ? cookie.substring(0, loc) : cookie;
        
    }
}