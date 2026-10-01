package davmail.imap;

import davmail.exchange.ExchangeSession;
import davmail.exchange.graph.GraphExchangeSession;
import junit.framework.TestCase;

/**
 * Unit tests for IMAP KEYWORD and UNKEYWORD search handling.
 */
public class TestSearchKeywords extends TestCase {

    private GraphExchangeSession session;
    private ImapConnection connection;

    @Override
    protected void setUp() {
        session = new GraphExchangeSession();
        connection = new ImapConnection(session);
    }

    private ExchangeSession.Message createMessage(String keywords) {
        ExchangeSession.Message message = session.new Message();
        message.keywords = keywords;
        return message;
    }

    public void testMessageHasKeyword() {
        ExchangeSession.Message msgNoKeywords = createMessage(null);
        assertFalse(msgNoKeywords.hasKeyword("Paperless"));
        assertFalse(msgNoKeywords.hasKeyword(null));
        assertFalse(msgNoKeywords.hasKeyword(""));
        assertFalse(msgNoKeywords.hasKeyword("   "));

        ExchangeSession.Message msgEmptyKeywords = createMessage("");
        assertFalse(msgEmptyKeywords.hasKeyword("Paperless"));

        ExchangeSession.Message msgSingleKeyword = createMessage("Paperless");
        assertTrue(msgSingleKeyword.hasKeyword("Paperless"));
        assertTrue(msgSingleKeyword.hasKeyword("paperless"));
        assertTrue(msgSingleKeyword.hasKeyword("PAPERLESS"));
        assertFalse(msgSingleKeyword.hasKeyword("Invoice"));

        ExchangeSession.Message msgMultipleKeywords = createMessage("Invoice, Paperless , Work");
        assertTrue(msgMultipleKeywords.hasKeyword("Invoice"));
        assertTrue(msgMultipleKeywords.hasKeyword("invoice"));
        assertTrue(msgMultipleKeywords.hasKeyword("Paperless"));
        assertTrue(msgMultipleKeywords.hasKeyword("paperless"));
        assertTrue(msgMultipleKeywords.hasKeyword("Work"));
        assertFalse(msgMultipleKeywords.hasKeyword("Personal"));
    }

    public void testMatchesKeywordsClientSide() {
        ImapConnection.SearchConditions conditions = new ImapConnection.SearchConditions();
        conditions.addUnkeyword("Paperless");

        ExchangeSession.Message unflaggedMsg = createMessage(null);
        assertTrue("Message with no categories must match UNKEYWORD Paperless",
                connection.matchesKeywords(conditions, unflaggedMsg));

        ExchangeSession.Message otherFlagMsg = createMessage("Invoice, Receipts");
        assertTrue("Message with other categories must match UNKEYWORD Paperless",
                connection.matchesKeywords(conditions, otherFlagMsg));

        ExchangeSession.Message paperlessMsg = createMessage("Paperless");
        assertFalse("Message with Paperless category must not match UNKEYWORD Paperless",
                connection.matchesKeywords(conditions, paperlessMsg));

        ExchangeSession.Message paperlessCaseMsg = createMessage("paperless, Archive");
        assertFalse("Message with lowercase paperless category must not match UNKEYWORD Paperless",
                connection.matchesKeywords(conditions, paperlessCaseMsg));
    }

    public void testMatchesKeywordsPositive() {
        ImapConnection.SearchConditions conditions = new ImapConnection.SearchConditions();
        conditions.addKeyword("Paperless");

        ExchangeSession.Message unflaggedMsg = createMessage(null);
        assertFalse("Message with no categories must not match KEYWORD Paperless",
                connection.matchesKeywords(conditions, unflaggedMsg));

        ExchangeSession.Message otherFlagMsg = createMessage("Invoice");
        assertFalse("Message with other category must not match KEYWORD Paperless",
                connection.matchesKeywords(conditions, otherFlagMsg));

        ExchangeSession.Message paperlessMsg = createMessage("Paperless");
        assertTrue("Message with Paperless category must match KEYWORD Paperless",
                connection.matchesKeywords(conditions, paperlessMsg));

        ExchangeSession.Message mixedMsg = createMessage("Invoice, paperless");
        assertTrue("Message with mixed categories containing paperless must match KEYWORD Paperless",
                connection.matchesKeywords(conditions, mixedMsg));
    }

    public void testBuildConditionsUnkeyword() throws Exception {
        ImapConnection.SearchConditions conditions = new ImapConnection.SearchConditions();
        ImapConnection.ImapTokenizer tokens = new ImapConnection.ImapTokenizer("UNKEYWORD Paperless");

        ExchangeSession.Condition condition = connection.buildConditions(conditions, tokens);

        assertTrue("UNKEYWORD must not produce a server-side filter condition",
                condition == null || condition.isEmpty());
        assertNotNull(conditions.unkeywords);
        assertTrue(conditions.unkeywords.contains("Paperless"));
    }

    public void testBuildConditionsSinceAndUnkeyword() throws Exception {
        ImapConnection.SearchConditions conditions = new ImapConnection.SearchConditions();
        ImapConnection.ImapTokenizer tokens = new ImapConnection.ImapTokenizer("(SINCE 1-Sep-2026 UNKEYWORD Paperless)");

        ExchangeSession.Condition condition = connection.buildConditions(conditions, tokens);

        assertNotNull("Server-side condition should contain the date filter", condition);
        StringBuilder buffer = new StringBuilder();
        condition.appendTo(buffer);
        String filter = buffer.toString();
        assertTrue("Filter should contain receivedDateTime ge", filter.contains("receivedDateTime ge"));
        assertFalse("Filter must NOT contain categories filter which breaks Graph search", filter.contains("categories"));

        assertNotNull(conditions.unkeywords);
        assertTrue(conditions.unkeywords.contains("Paperless"));
    }

    public void testBuildConditionsKeyword() throws Exception {
        ImapConnection.SearchConditions conditions = new ImapConnection.SearchConditions();
        ImapConnection.ImapTokenizer tokens = new ImapConnection.ImapTokenizer("KEYWORD Paperless");

        ExchangeSession.Condition condition = connection.buildConditions(conditions, tokens);

        assertNotNull("Server-side condition should contain categories filter", condition);
        StringBuilder buffer = new StringBuilder();
        condition.appendTo(buffer);
        assertEquals("categories/any(a:a eq 'Paperless')", buffer.toString());

        assertNotNull(conditions.keywords);
        assertTrue(conditions.keywords.contains("Paperless"));
    }

    public void testBuildConditionsNotKeyword() throws Exception {
        ImapConnection.SearchConditions conditions = new ImapConnection.SearchConditions();
        ImapConnection.ImapTokenizer tokens = new ImapConnection.ImapTokenizer("NOT KEYWORD Paperless");

        ExchangeSession.Condition condition = connection.buildConditions(conditions, tokens);

        assertTrue("NOT KEYWORD must not produce a server-side filter condition",
                condition == null || condition.isEmpty());
        assertNotNull(conditions.unkeywords);
        assertTrue(conditions.unkeywords.contains("Paperless"));
    }

    public void testBuildConditionsNotParenthesizedKeyword() throws Exception {
        ImapConnection.SearchConditions conditions = new ImapConnection.SearchConditions();
        ImapConnection.ImapTokenizer tokens = new ImapConnection.ImapTokenizer("NOT (KEYWORD Paperless)");

        ExchangeSession.Condition condition = connection.buildConditions(conditions, tokens);

        assertTrue("NOT (KEYWORD Paperless) must not produce a server-side filter condition",
                condition == null || condition.isEmpty());
        assertNotNull(conditions.unkeywords);
        assertTrue(conditions.unkeywords.contains("Paperless"));
    }

    public void testBuildConditionsNotUnkeyword() throws Exception {
        ImapConnection.SearchConditions conditions = new ImapConnection.SearchConditions();
        ImapConnection.ImapTokenizer tokens = new ImapConnection.ImapTokenizer("NOT UNKEYWORD Paperless");

        ExchangeSession.Condition condition = connection.buildConditions(conditions, tokens);

        assertNotNull("NOT UNKEYWORD should translate to positive server-side filter", condition);
        StringBuilder buffer = new StringBuilder();
        condition.appendTo(buffer);
        assertEquals("categories/any(a:a eq 'Paperless')", buffer.toString());

        assertNotNull(conditions.keywords);
        assertTrue(conditions.keywords.contains("Paperless"));
    }

}
