package com.idega.block.websearch.business;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.ArrayList;
import java.util.List;

import javax.swing.text.html.parser.ParserDelegator;
import javax.swing.text.html.HTMLEditorKit.ParserCallback;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML.Tag;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import javax.swing.text.html.HTML;


/**
 * <p><code>HTMLHandler</code>
 *  Content handler for HTML documents.</p>
* This class is a part of the websearch webcrawler and search engine block. <br>
* It is based on the <a href="http://lucene.apache.org">Lucene</a> java search engine from the Apache group and loosly <br>
* from the work of David Duddleston of i2a.com.<br>
*
* @copyright Idega Software 2002
* @author <a href="mailto:eiki@idega.is">Eirikur Hrafnsson</a>
 */

public final class HTMLHandler extends ParserCallback implements ContentHandler{
    
    // Content
    private String title;
    private String description;
    private String keywords;
    private String categories;
    private long published;
    private String href;
    private String author;
    private StringBuffer contents;
    private ArrayList links;
    
    // Robot Instructions
    private boolean robotIndex;
    private boolean robotFollow;
    
    private static final char space = ' ';
    private char state;
    private static final char NONE = 0;
    private static final char TITLE = 1;
    private static final char HREF = 2;
    private static final char SCRIPT = 3;
    
    private SimpleDateFormat dateFormatter;
    private static ParserDelegator pd = new ParserDelegator();
    
    /**
     *		Constructor - initializes variables
     */
    public HTMLHandler() {
        
        contents = new StringBuffer();
        links = new ArrayList();
        published = -1;
        
        // 1996.07.10 15:08:56 PST
        dateFormatter = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss z");
    }
    /**
     * Parse Content. [24] 320:1
     */
    public String getAuthor() {
        return author;
    }
    /**
     * Return categories (from META tags)
     */
    public String getCategories() {
        return this.categories;
    }
    /**
     *	Return contents
     */
    public String getContents() {
        return this.contents.toString();
    }
    /**
     *	Return description (from META tags)
     */
    public String getDescription() {
        return this.description;
    }
    /**
     *	Return META HREF
     */
    public String getHREF() {
        return this.href;
    }
    /**
     * Return keywords (from META tags)
     */
    public String getKeywords() {
        return this.keywords;
    }
    /**
     * Return links
     */
    public List getLinks() {
        return links;
    }
    /**
     *	Return published date (from META tag)
     */
    public long getPublished() {
        return this.published;
    }
    /**
     * Return boolean true if links are to be followed
     */
    public boolean getRobotFollow() {
        return this.robotFollow;
    }
    /**
     * Return boolean true if this is to be indexed
     */
    public boolean getRobotIndex() {
        return this.robotIndex;
    }
    /**
     *		Return page title
     */
    public String getTitle() {
        return this.title;
    }
    /**
     *		Handle Anchor <A HREF="~"> tags
     */
    public void handleAnchor(MutableAttributeSet attribs) {
        String href = new String();
        href = (String) attribs.getAttribute(HTML.Attribute.HREF);
        if (href == null)
            return;
        links.add(href);
        state = HREF;
    }
    /**
     *		Closing tag
     */
    public void handleEndTag(Tag tag, int pos) {
        if (state == NONE)
            return;
        // In order of precedence == > && > ||
        if (state == TITLE && tag.equals(HTML.Tag.TITLE)) {
            state = NONE;
            return;
        }
        if (state == HREF && tag.equals(HTML.Tag.A)) {
            //links.add(linktext);
            state = NONE;
            return;
        }
        if (state == SCRIPT && tag.equals(HTML.Tag.SCRIPT)) {
            state = NONE;
            return;
        }
    }
    /**
     *		Handle META tags
     */
    public void handleMeta(MutableAttributeSet attribs) {
        String name = new String();
        String content = new String();
        name = (String) attribs.getAttribute(HTML.Attribute.NAME);
        content = (String) attribs.getAttribute(HTML.Attribute.CONTENT);
        if (name == null || content == null)
            return;
        name = name.toUpperCase();
        if (name.equals("DESCRIPTION")) {
            description = content;
            return;
        }
        if (name.equals("KEYWORDS")) {
            keywords = content;
            return;
        }
        if (name.equals("CATEGORIES")) {
            categories = content;
            return;
        }
        if (name.equals("PUBLISHED")) {
            try {
                published = dateFormatter.parse(content).getTime();
            } catch(ParseException e) {e.printStackTrace();}
            return;
        }
        if (name.equals("HREF")) {
            href = content;
            return;
        }
        if (name.equals("AUTHOR")) {
            author = content;
            return;
        }
        if (name.equals("ROBOTS")) {
            
            if (content.indexOf("noindex") != -1) robotIndex = false;
            if (content.indexOf("nofollow") != -1) robotFollow = false;
            
            author = content;
            return;
        }
    }
    /**
     *	Handle standalone tags
     */
    public void handleSimpleTag(Tag tag, MutableAttributeSet attribs, int pos) {
        if (tag.equals(HTML.Tag.META)) {
            handleMeta(attribs);
        }
    }
    /**
     *		Opening tag
     */
    public void handleStartTag(Tag tag, MutableAttributeSet attribs, int pos) {
        if (tag.equals(HTML.Tag.TITLE)) {
            state = TITLE;
        } else if (tag.equals(HTML.Tag.A)) {
            handleAnchor(attribs);
        } else if (tag.equals(HTML.Tag.SCRIPT)) {
            state = SCRIPT;
        }
    }
    /**
     *		Handle page text
     */
    public void handleText(char[] text, int pos) {
        switch (state) {
            case NONE :
                contents.append(text);
                contents.append(space);
                break;
            case TITLE :
                title = new String(text);
                break;
            case HREF :
                contents.append(text);
                contents.append(space);
                //linktext = new String(text);
                break;
        }
    }
    /**
     * Parse Content.
     */
    public void parse(InputStream in) {
        
        try {
            reset();
            
            pd.parse(new BufferedReader(new InputStreamReader(in)), this, true);
            
            //System.out.println("Title: " + getTitle());
            //System.out.println("Author: " + getAuthor());
            //System.out.println("Published " + getPublished());
            //System.out.println("Keywords: " + getKeywords());
            //System.out.println("Description: " + getDescription());
            //System.out.println("Content: " + getContents());
            
        } catch (Exception e) {e.printStackTrace();}
        
    }
    /**
     *	Return contents
     */
    private void reset() {
        title = null;
        description = null;
        keywords = null;
        categories = null;
        href = null;
        author = null;
        
        contents.setLength(0);
        links = new ArrayList();
        published = -1;
        
        // Robot Instructions
        robotIndex = true;
        robotFollow = true;
        
        state = NONE;
        
    }
}