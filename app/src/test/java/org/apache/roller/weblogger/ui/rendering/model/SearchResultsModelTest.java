/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.  For additional information regarding
 * copyright in this work, please see the NOTICE file in the top level
 * directory of this distribution.
 */

/* Created on March 8, 2023 */

package org.apache.roller.weblogger.ui.rendering.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.util.RollerConstants;
import org.apache.roller.weblogger.TestUtils;
import org.apache.roller.weblogger.business.WeblogEntryManager;
import org.apache.roller.weblogger.business.WebloggerFactory;
import org.apache.roller.weblogger.business.search.LuceneIndexManagerTest;
import org.apache.roller.weblogger.business.search.lucene.AddEntryOperation;
import org.apache.roller.weblogger.business.search.lucene.LuceneIndexManager;
import org.apache.roller.weblogger.business.search.lucene.SearchOperation;
import org.apache.roller.weblogger.pojos.User;
import org.apache.roller.weblogger.pojos.Weblog;
import org.apache.roller.weblogger.pojos.WeblogCategory;
import org.apache.roller.weblogger.pojos.WeblogEntry;
import org.apache.roller.weblogger.ui.rendering.util.WeblogPageRequest;
import org.apache.roller.weblogger.ui.rendering.util.WeblogSearchRequest;
import org.instancio.Instancio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.roller.weblogger.ui.rendering.util.WeblogSearchRequest.SEARCH_SERVLET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchResultsModelTest {
    User testUser = null;
    Weblog testWeblog = null;
    public static Log log = LogFactory.getLog(LuceneIndexManagerTest.class);

    @BeforeEach
    public void setUp() throws Exception {
        TestUtils.setupWeblogger();
        try {
            testUser = TestUtils.setupUser("entryTestUser");
            testWeblog = TestUtils.setupWeblog("entryTestWeblog", testUser);
            TestUtils.endSession(true);
        } catch (Exception ex) {
            log.error("ERROR in test setup", ex);
            throw new Exception("Test setup failed", ex);
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        try {
            TestUtils.teardownWeblog(testWeblog.getId());
            TestUtils.teardownUser(testUser.getUserName());
            TestUtils.endSession(true);
        } catch (Exception ex) {
            log.error("ERROR in test teardown", ex);
            throw new Exception("Test teardown failed", ex);
        }
    }

    @Test
    void getResults() throws Exception {

        // create some entries and index them
        List<WeblogEntry> entries = Instancio.ofList(WeblogEntry.class).size(10).create();
        entries.get(0).setTitle("The Tholian Web");
        entries.get(0).setText(
            "When the Enterprise attempts to ascertain the fate of the  "
            +"U.S.S. Defiant which vanished 3 weeks ago, the warp engines  "
            +"begin to lose power, and Spock reports strange sensor readings.");
        entries.get(1).setTitle("A Piece of the Action");
        entries.get(1).setText(
            "The crew of the Enterprise attempts to make contact with "
            +"the inhabitants of planet Sigma Iotia II, and Uhura puts Kirk "
            +"in communication with Boss Oxmyx.");

        LuceneIndexManager indexManager =
            (LuceneIndexManager)WebloggerFactory.getWeblogger().getIndexManager();

        WeblogEntryManager entryManager = WebloggerFactory.getWeblogger().getWeblogEntryManager();
        for (WeblogEntry entry : entries) {
            WeblogCategory cat = entryManager.getWeblogCategory(
                testWeblog.getWeblogCategory("General").getId());
            entry.setCategory(cat);
            entry.setWebsite(TestUtils.getManagedWebsite(testWeblog));
            entry.setEntryAttributes(Collections.emptySet());
            entry.setTags(Collections.emptySet());
            entryManager.saveWeblogEntry(entry);
            TestUtils.endSession(true);

            entry = TestUtils.getManagedWeblogEntry(entry);
            indexManager.executeIndexOperationNow(new AddEntryOperation(
                WebloggerFactory.getWeblogger(),
                indexManager,
                TestUtils.getManagedWeblogEntry(entry)));
        }

        Thread.sleep(RollerConstants.SEC_IN_MS);

        try {

            SearchOperation search = new SearchOperation(indexManager);
            search.setTerm("Enterprise");
            search.setWeblogHandle(testWeblog.getHandle());
            indexManager.executeIndexOperationNow(search);
            assertEquals(2, search.getResultsCount());

            SearchOperation search2 = new SearchOperation(indexManager);
            search2.setTerm("Tholian");
            search2.setWeblogHandle(testWeblog.getHandle());
            indexManager.executeIndexOperationNow(search2);
            assertEquals(1, search2.getResultsCount());

            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getServletPath()).thenReturn(SEARCH_SERVLET);
            when(request.getRequestURL()).thenReturn(
                new StringBuffer(String.format("http://localhost/%s", SEARCH_SERVLET)));
            when(request.getPathInfo()).thenReturn(null);

            WeblogSearchRequest searchRequest = new WeblogSearchRequest(request);
            searchRequest.setWeblogHandle(testWeblog.getHandle());
            searchRequest.setQuery("Enterprise");

            WeblogPageRequest pageRequest = new WeblogPageRequest();

            Map<String, Object> initData = new HashMap<>();
            initData.put("searchRequest", searchRequest);
            initData.put("parsedRequest", pageRequest);
            initData.put("pageRequest", pageRequest);

            SearchResultsModel model = new SearchResultsModel();
            model.init(initData);

            assertEquals(2, model.getResults().size());

        } finally {
            for (WeblogEntry entry : entries) {
                indexManager.removeEntryIndexOperation(TestUtils.getManagedWeblogEntry(entry));
            }
        }
    }
}