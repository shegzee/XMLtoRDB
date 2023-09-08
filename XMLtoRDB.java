
import java.util.*;

import java.io.*;
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import org.w3c.dom.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
// import java.util.Date;

public class XMLtoRDB
{
	String result = new String();
	// String filename = "xmlfile.xml";

	private Connection connect = null;
	private Statement statement = null;
	private PreparedStatement preparedStatement = null;
	private ResultSet resultSet = null;
	// private String dbname = "XR";

	public static void main(String[] args) throws SQLException, ClassNotFoundException, InstantiationException, IllegalAccessException
	{
		XMLtoRDB XR = new XMLtoRDB();
		int id = XR.XR("xmlfile.xml", "XR");
	}

	public int XR(String filename, String dbname) throws SQLException, ClassNotFoundException, InstantiationException, IllegalAccessException
	{
		// Class.forName("com.mysql.cj.jdbc.Driver").newInstance();
		int docID = 0;

		this.connect = DriverManager.getConnection("jdbc:mysql://localhost/" + dbname + "?user=root&password=&serverTimezone=UTC");
		// this.preparedStatement = this.connect.prepareStatement("insert into Document (docName) values (?)", new String[] { "id" }/*Statement.RETURN_GENERATED_KEYS*/);
		this.preparedStatement = this.connect.prepareStatement("insert into documents (docName) values (?)", Statement.RETURN_GENERATED_KEYS);
		this.preparedStatement.setString(1, filename);
		if (this.preparedStatement.executeUpdate() > 0)
		{
			java.sql.ResultSet generatedKeys = this.preparedStatement.getGeneratedKeys();

			if (generatedKeys.next()) docID = generatedKeys.getInt(1);
		}

		Node T = parseXML(new File(filename));
		String root = T.getNodeValue();
		int root_id = insertIntoDB(T, 0, docID, 0, 0);
		this.connect.close();
		return root_id;
	}

	public int insertIntoDB(Node node, int parent_id, int doc_id, int depth, int previous_id) throws SQLException
	{
		// whether or not to insert this particular node
		boolean doInsert = true;

		int docID = doc_id;
		String elmName = node.getNodeName();
		String elmValue = node.getNodeValue();
		int parentID = parent_id;
		// order_id not inserted yet. This will be done with the next element via the given "previous_id"

		// insert this element only if it is a proper xml element
		if (node instanceof Document)
		{
			doInsert = false;
		}
		
		// hacks to make this algorithm's results conform to the pdf's requirements
		// squash #text nodes together with proper nodes
		Node firstChild = node.getFirstChild();
		if (node instanceof Text)
		{
			// skip #text nodes
			// this.preparedStatement = this.connect.prepareStatement("update elements (elmValue) values (?) where elmID = ?");
			// this.preparedStatement.setString(1, node.getNodeValue());
			// this.preparedStatement.setInt(2, parent_id);
			return 0;
		} else if (firstChild instanceof Text)
		{
			// if first child is a #text node, get the value and use it for present node value
			if (!firstChild.getNodeValue().trim().equals(""))
			{
				elmValue = firstChild.getNodeValue();
			}
		}

		// to make the depth field as defined by the pdf
		if (elmValue != null)
		{
			++depth;
		}
		int elmDepth = depth;

		int elmID = 0;
		if (doInsert)
		{

			// insert element
			if (parentID != 0)
			{
				// has parent
				this.preparedStatement = this.connect.prepareStatement("insert into elements (docID, elmName, elmValue, parentID, elmDepth) values (?, ?, ?, ?, ?)", new String[] { "id" }/*Statement.RETURN_GENERATED_KEYS*/);
				this.preparedStatement.setInt(4, parentID);
				this.preparedStatement.setInt(5, elmDepth);
			} else
			{
				// keep parentID field as null
				this.preparedStatement = this.connect.prepareStatement("insert into elements (docID, elmName, elmValue, elmDepth) values (?, ?, ?, ?)", new String[] { "id" }/*Statement.RETURN_GENERATED_KEYS*/);
				this.preparedStatement.setInt(4, elmDepth);
			}
			this.preparedStatement.setInt(1, docID);
			this.preparedStatement.setString(2, elmName);
			this.preparedStatement.setString(3, elmValue);
			// get insert id
			if (this.preparedStatement.executeUpdate() > 0)
			{
				java.sql.ResultSet generatedKeys = this.preparedStatement.getGeneratedKeys();

				if (generatedKeys.next()) elmID = generatedKeys.getInt(1);
			}

			// add "order_id" to previous node (on same level)
			if (previous_id != 0)
			{
				this.preparedStatement = this.connect.prepareStatement("update elements set orderID = ? where elmID = ?");
				this.preparedStatement.setInt(1, elmID);
				this.preparedStatement.setInt(2, previous_id);
				this.preparedStatement.executeUpdate();
			}

			// add attributes to db
			NamedNodeMap atts = node.getAttributes();
			if (atts != null) {
				for (int i=0; i<atts.getLength(); i++)
				{
					Node att = atts.item(i);
					String attName = att.getNodeName();
					String attValue = att.getNodeValue();

					this.preparedStatement = this.connect.prepareStatement("insert into attributes (elmID, attName, attValue) values (?, ?, ?)");
					this.preparedStatement.setInt(1, elmID);
					this.preparedStatement.setString(2, attName);
					this.preparedStatement.setString(3, attValue);
					this.preparedStatement.executeUpdate();
				}
			}

		}

		// do children
		int previousChildId = 0;
		for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling())
		{
			previousChildId = insertIntoDB(child, elmID, docID, depth, previousChildId);
		}

		return elmID;
	}

	public static Node parseXML(File xmlFile)
	{
		// Java's in-built DOM parser does not produce the same kinda results as defined in the pdf
		// create DocumentBuilderFactory
		DocumentBuilderFactory documentFactory;
		documentFactory = DocumentBuilderFactory.newInstance();
	
		// configure it
		documentFactory.setValidating(false);
		documentFactory.setIgnoringComments(true);
		documentFactory.setIgnoringElementContentWhitespace(true);
		documentFactory.setCoalescing(false);
		documentFactory.setExpandEntityReferences(true);
	
		// create DocumentBuilder from the factory
		DocumentBuilder myDocumentBuilder = null;
	
		try {
			myDocumentBuilder = documentFactory.newDocumentBuilder();
		} catch (ParserConfigurationException pce) {
			System.err.println(pce);
			System.exit(1);
		}
	
		// Parse the input file
		Document parsedDocument = null;
		try {
			parsedDocument = myDocumentBuilder.parse(xmlFile);
		} catch (SAXException se) {
			System.err.println(se.getMessage());
			System.exit(1);
		} catch (IOException ioe) {
			System.err.println(ioe);
			System.exit(1);
		}

		// parsedDocument contains the parsed document
		// let's return it
		return parsedDocument;
	}

}