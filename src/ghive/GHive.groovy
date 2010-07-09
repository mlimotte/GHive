package ghive;

import groovy.sql.Sql
import java.sql.ResultSet
import java.sql.Statement
import java.text.SimpleDateFormat

public class GHive {

	private static final GHive instance = new GHive();

	private static final String DRIVER = "org.apache.hadoop.hive.jdbc.HiveDriver";
	private static final String CONNECTION_STRING = "jdbc:hive://";
	private static final Sql db = Sql.newInstance(CONNECTION_STRING, "", "", DRIVER);
	private static final Statement stmt = db.connection.createStatement()

	private static final SDF_DAY = new SimpleDateFormat("yyyy-MM-dd")
	private static final SDF_HOUR = new SimpleDateFormat("HH")
	public  static final SDF_INPUT = new SimpleDateFormat("yyyy-MM-dd/HH")

	private GHive() {
	}

	public String dayFormat(Date d) {
		SDF_DAY.format(d)
	}
	public String hourFormat(Date d) {
		SDF_HOUR.format(d)
	}

	/**
	 * Relative path inside the classpath
	 * @param resourcePath
	 * @return
	 */
	public InputStream getStream(String resourcePath) {
		return getClass().getClassLoader().getResourceAsStream(resourcePath)
	}

	/**
	 * Execute the given script through Hive.
   * @param hiveSql A valid Hive statement, with no ";" at the end.
   * @param c Ignored if null, otherwise execute the closure, passing the ResultSet as the argument
	 */
	public void execute(String hiveSql, Closure c) {
		ResultSet rs = null
		try {
			rs = stmt.executeQuery(hiveSql);
			if (c!=null) {
		  	c.call(rs)
			}
		} finally {
			// close() is not supported/necessary for this implementation of ResultSet
			//if (rs != null) rs.close()
		}
	}

  /**
   * execute the query and get all the results
   * @param hiveSql - Should a query that produces a result set (i.e. a select stmt)
   * @param colNames - HiveResultSet does not return metadata, so supply a list of names in order of the columns in the select
   * @return Each map in the list is one row from the result set, and contains each column name mapped to it's value
   */
  public List<Map<String,String>> executeAndGetList(String hiveSql, List<String> colNames) {
    def result = []
    eachRow(hiveSql) { rs ->
      def row = [:]
      def i=1
      colNames.each { col ->
        row[col] = rs.getString(i++)
      }
      result << row
    }
    return result
  }

	public void execute(String hiveSql) {
		execute(hiveSql, null)
	}

  /**
   * Execute the given script through Hive.
   * Example:<br/>
   * <code>
   *   ghive.eachRow("select name from users") { rs ->
   *     println rs.getString(1)
   *   }
   * </code>
   * getXXX() methods must use an index (starting at 1), not a string column name (b/c it's not supported by the Hive JDBC driver.<br/>
   * @param hiveSql A valid Hive statement, with no ";" at the end.
   * @param c Ignored if null, otherwise execute the closure once for each row in the result, passing the ResultSet as the argument
   */
	public void eachRow(String hiveSql, Closure c) {
    ResultSet rs = null
    try {
      rs = stmt.executeQuery(hiveSql);
      if (c!=null) {
        while (rs.next()) {
          c.call(rs)
        }
      }
    } finally {
      // close() is not supported/necessary for this implementation of ResultSet
      //if (rs != null) rs.close()
    }
	}

  /**
   * Execute the given script through Hive.
   * Example:<br/>
   * <code>
   *   ghive.eachRow("select name from users") { rs ->
   *     println rs.getString(1)
   *   }
   * </code>
   * getXXX() methods must use an index (starting at 1), not a string column name (b/c it's not supported by the Hive JDBC driver.<br/>
   * @param path A path to  GHIve script.  This script must contain only one hive statement
   * @paran vars A Map of variable names to values
   * @param c Ignored if null, otherwise execute the closure once for each row in the result, passing the ResultSet as the argument
   */
	public void eachRow(String path, Map vars, Closure c) {
    def cmds = parseScript(path,vars)
    if (cmds.size() != 1) throw new IllegalArgumentException("The script for each row must contain a single hive statement.")
    eachRow(cmds[0], c)
	}

	/**
	 * @see GHive::executeScript
	 */
	public List<String> parseScript(String path, Map vars) {
		def commands = []
		def buf = new StringBuffer()
		getStream(path).eachLine { line ->
			// strip comments
			line = line.replaceFirst(/^\s*--.*$/,'')
			// variable interpolation
			line = line.replaceAll(/\$\{(\w+)\}/) { Object[] varMatch -> vars[varMatch[1]] }
			// find end of command and execute
			def m = line =~ /(.*);\s*/
			if (m) {
				buf << m[0][1] << "\n"
				commands << buf.toString()
				buf.setLength(0)
			} else {
				// don't add the line if it only contains whitespace
				if (line =~ /\S/) {
					buf << line << "\n"
				}
			}
		}
		def lastCommand = buf.toString()
		if (lastCommand.length() > 0) {
			commands << buf.toString()
		}
		return commands
	}

	/**
	 * Parse the script and dump the resulting text of the script into a file.  This is useful for
   * debugging/logging.
	 *
	 * @param The path of the script
	 * @param vars A map of variables
	 * @param dumpPath Path where the result should be ouput.  The file will be overwritten.
	 */
	public void dumpScript(String path, Map vars, String dumpPath) {
  	def dump = new File(dumpPath)
  	dump.delete()
  	parseScript(path,vars).each {
  		dump << it.replaceFirst(/\n$/,'') + ";\n\n"
  	}
	}

	/**
	 * Execute the specified script as Hive commands. Several pre-processing steps are done first</br>
	 * <ul>
	 * <li>Strip comments: Lines starting with -- (or whitespace then --) to end of line </li>
	 * <li>Variable interpolation: The script can include variables of the form ${name} which are replaced
   * by values from the vars Map.</li>
	 * <li>Multiple commands can be specified in the script and they will be executed in order. Each
   * command can be terminate by ; or EOF.</li>
	 * </ul>
	 * @param path A path to a file, which is found using the ClassLoader getResourceAsStream
	 * @param var A map of variables names (case-sensitive strings with word characters only) to their
   *        values for variable interpolation
	 */
	public void executeScript(String path, Map vars) {
		parseScript(path, vars).each { execute(it) }
	}

	public void executeScript(String path) {
		executeScript(path, [:])
	}

  /**
   * Factory method to get a single instance of this class.
   * @return
   */
	public static GHive instance() {
		return instance
	}

}