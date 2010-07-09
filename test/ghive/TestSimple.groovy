package ghive

import org.junit.AfterClass
import org.junit.Before
import org.junit.Test

/**
 * A sample unit test of hive script with variables. Does variable replacement 
 * and runs against configured hadoop cluster (usually a single-process, standalone cluster).
 * </br>
 * Setup notes:
 * <ul>
 * <li>Make sure HADOOP_HOME is set in the environment (for Eclipse, needs to be set in the Run Configuration)</li>
 * <li>hadoop-*-core.jar and lib/*.jar and conf/ should be on the classpath</li> 
 * <li>The hadoop configuration should be set up for standalone mode, so you don't need to start up a cluster for the tests</li>
 * <li>hive lib/*jar and hive conf/ should be on the classpath</li>
 * </ul>
 * @author mlimotte               
 */

public class TestSimple extends GroovyTestCase {

	static final GHive ghive = GHive.instance()

	@Before
	public void setUp() throws Exception {
    ghive.execute("DROP TABLE simple")   // drop table
	}

	@Test
	public void testSimple() {

    // the q path is relative to the classpath
		def q = "simple.hive"

    def queries = ghive.parseScript (q, [ STORAGE_TYPE: 'TEXTFILE' ])

		ghive.execute(queries[0])   // create table
		ghive.execute(queries[1])   // load data
    def result = ghive.executeAndGetList(
        "select id, value, amt from simple",
        [ 'id', 'value', 'amt' ])

    assertEquals(3,result.size())
    
    def expected = [
  		[ id:'1', value:'line1', amt:'0.2' ],
  		[ id:'100', value:'line2', amt:'0.3' ],
  		[ id:'50', value:'line3', amt:'0.4' ]
		]
		assertEquals(expected,result)

	}

	@AfterClass
	public static void classTearDown() {
		ghive.execute("DROP TABLE simple")   // drop table
	}

}