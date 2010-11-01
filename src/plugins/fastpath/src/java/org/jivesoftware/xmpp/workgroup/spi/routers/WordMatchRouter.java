/**
 * $RCSfile$
 * $Revision: 32902 $
 * $Date: 2006-08-04 11:11:39 -0700 (Fri, 04 Aug 2006) $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup.spi.routers;

import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.request.Request;
import org.jivesoftware.xmpp.workgroup.request.UserRequest;
import org.jivesoftware.xmpp.workgroup.routing.RequestRouter;
import org.jivesoftware.xmpp.workgroup.utils.ModelUtil;
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.RAMDirectory;
import org.jivesoftware.util.Log;

import java.io.Reader;
import java.util.List;
import java.util.Map;

/**
 * The WordMatcheRouter using Lucense index to search individual metadata as specified
 * in routing rules.
 */
public class WordMatchRouter extends RequestRouter {

    private boolean stemmingEnabled;
    private Analyzer analyzer;

    /**
     * Constructs a new word match router.
     */
    public WordMatchRouter() {
        analyzer = new StandardAnalyzer();
    }

    /**
     * Returns true if stemming will be applied to keywords. Stemming is a mechanism
     * for matching multiple versions of the same word. For example, when stemming is
     * enabled the word "cats" will match "cat" and "thrill" will match "thrilling".
     * So, stemming makes the keyword list easier to manage when you to be notified
     * of any version of a particular word.<p/>
     * 
     * The stemming implementation uses the Porter algorithm, which is only suitable
     * for English text. If your content is non-english, stemming should be disabled.
     *
     * @return true if stemming is enabled.
     */
    public boolean isStemmingEnabled() {
        return stemmingEnabled;
    }

    /**
     * Toggles whether stemming will be applied to keywords. Stemming is a mechanism
     * for matching multiple versions of the same word. For example, when stemming is
     * enabled the word "cats" will match "cat" and "thrill" will match "thrilling".<p/>
     * 
     * The stemming implementation uses the Porter algorithm, which is only suitable
     * for English text. If your content is non-english, stemming should be disabled.
     *
     * @param stemmingEnabled true if stemming should be enabled.
     */
    public void setStemmingEnabled(boolean stemmingEnabled) {
        // If not changing the value, do nothing.
        if (this.stemmingEnabled == stemmingEnabled) {
            return;
        }
        if (stemmingEnabled) {
            // Turn of stemming.
            this.stemmingEnabled = true;
            analyzer = new StemmingAnalyzer();
        }
        else {
            // Turn off stemming.
            this.stemmingEnabled = false;
            analyzer = new StandardAnalyzer();
        }
    }

    public boolean handleRequest(Workgroup workgroup, UserRequest request) {
        return false;
    }

    public boolean search(Workgroup workgroup, Request request, String queryString) {
        return checkForHits(request.getMetaData(), queryString);
    }

    /**
     * Returns true if the query string matches results in the request map.
     *
     * @param requestMap the map of request meta data. Each map key is a String with a value
     *      of a list of Strings.
     * @param queryString the query to test against the map.
     * @return true if the query string matches the request.
     */
    public boolean checkForHits(Map<String, List<String>> requestMap, String queryString) {
        // Enable stemming.
        setStemmingEnabled(true);

        boolean foundMatch = false;
        try {
            // Create an in-memory directory.
            RAMDirectory dir = new RAMDirectory();
            // Index the message.
            IndexWriter writer = new IndexWriter(dir, analyzer, true);

            BooleanQuery booleanQuery = new BooleanQuery();
            Document doc = new Document();

            for (String key: requestMap.keySet()) {
                List<String> keyValue = requestMap.get(key);
                if (keyValue != null) {
                    StringBuilder builder = new StringBuilder();
                    for (String value : keyValue) {
                        if (ModelUtil.hasLength(value)) {
                            builder.append(value);
                            builder.append(" ");
                        }
                    }

                    // Add to Search Indexer
                    doc.add(new Field(key, builder.toString(), Field.Store.YES,
                            Field.Index.TOKENIZED));

                    QueryParser parser = new QueryParser(key, analyzer);
                    Query query = parser.parse(queryString);
                    booleanQuery.add(query, BooleanClause.Occur.MUST);
                }
            }

            writer.addDocument(doc);
            writer.close();

            // Create a searcher, try to find a match.
            IndexSearcher searcher = new IndexSearcher(dir);

            Hits hits = searcher.search(booleanQuery);
            // Check to see if a match was found.
            if (hits.length() > 0) {
                foundMatch = true;
            }
            searcher.close();
        }
        catch (Exception e) {
            Log.error(e);
        }

        return foundMatch;
    }

    /**
     * A Lucene Analyzer that does stemming.
     */
    private class StemmingAnalyzer extends Analyzer {
        public final TokenStream tokenStream(String fieldName, Reader reader) {
            // Apply stop words and porter stemmer using a lower-case tokenizer.
            TokenStream stream = new StopFilter(new LowerCaseTokenizer(reader),
                StandardAnalyzer.STOP_WORDS);
            return new PorterStemFilter(stream);
        }
    }
}