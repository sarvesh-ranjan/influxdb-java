package org.influxdb;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.influxdb.InfluxDB.LogLevel;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Pong;
import org.influxdb.dto.Query;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;

/**
 * Test the InfluxDB API.
 * 
 * @author stefan.majer [at] gmail.com
 * 
 */
@Test
public class InfluxDBTest {

	private InfluxDB influxDB;
	private DockerClient dockerClient;
	private CreateContainerResponse container;

	/**
	 * Create a influxDB connection before all tests start.
	 * 
	 * @throws InterruptedException
	 * @throws IOException
	 */
	@BeforeClass
	public void setUp() throws InterruptedException, IOException {
		// Disable logging for the DockerClient.
		Logger.getLogger("com.sun.jersey").setLevel(Level.OFF);
		DockerClientConfig config = DockerClientConfig
				.createDefaultConfigBuilder()
				.withVersion("1.16")
				.withUri("tcp://localhost:4243")
				.withUsername("roott")
				.withPassword("root")
				.build();
		this.dockerClient = DockerClientBuilder.getInstance(config).build();
		// this.dockerClient.pullImageCmd("majst01/influxdb-java");

		// ExposedPort tcp8086 = ExposedPort.tcp(8086);
		//
		// Ports portBindings = new Ports();
		// portBindings.bind(tcp8086, Ports.Binding(8086));
		// this.container = this.dockerClient.createContainerCmd("influxdb:0.9.0-rc7").exec();
		// this.dockerClient.startContainerCmd(this.container.getId()).withPortBindings(portBindings).exec();
		//
		// InspectContainerResponse inspectContainerResponse =
		// this.dockerClient.inspectContainerCmd(
		// this.container.getId()).exec();
		//
		// InputStream containerLogsStream = this.dockerClient
		// .logContainerCmd(this.container.getId())
		// .withStdErr()
		// .withStdOut()
		// .exec();

		// String ip = inspectContainerResponse.getNetworkSettings().getIpAddress();
		String ip = "127.0.0.1";
		this.influxDB = InfluxDBFactory.connect("http://" + ip + ":8086", "root", "root");
		boolean influxDBstarted = false;
		do {
			Pong response;
			try {
				response = this.influxDB.ping();
				System.out.println(response);
				if (!response.getVersion().equalsIgnoreCase("unknown")) {
					influxDBstarted = true;
				}
			} catch (Exception e) {
				// NOOP intentional
				e.printStackTrace();
			}
			Thread.sleep(100L);
		} while (!influxDBstarted);
		this.influxDB.setLogLevel(LogLevel.FULL);
		// String logs = CharStreams.toString(new InputStreamReader(containerLogsStream,
		// Charsets.UTF_8));
		System.out.println("##################################################################################");
		// System.out.println("CContainer Logs: \n" + logs);
		System.out.println("#  Connected to InfluxDB Version: " + this.influxDB.version() + " #");
		System.out.println("##################################################################################");
	}

	/**
	 * Ensure all Databases created get dropped afterwards.
	 */
	@AfterClass
	public void tearDown() {
		System.out.println("Kill the Docker container");
		// this.dockerClient.killContainerCmd(this.container.getId()).exec();
	}

	/**
	 * Test for a ping.
	 */
	@Test(enabled = true)
	public void testPing() {
		Pong result = this.influxDB.ping();
		Assert.assertNotNull(result);
		Assert.assertNotEquals(result.getVersion(), "unknown");
	}

	/**
	 * Test that version works.
	 */
	@Test(enabled = true)
	public void testVersion() {
		String version = this.influxDB.version();
		Assert.assertNotNull(version);
		Assert.assertFalse(version.contains("unknown"));
	}

	/**
	 * Simple Test for a query.
	 */
	@Test(enabled = true)
	public void testQuery() {
		this.influxDB.query(new Query("CREATE DATABASE mydb2", "mydb"));
		this.influxDB.query(new Query("DROP DATABASE mydb2", "mydb"));
	}

	/**
	 * Test that describe Databases works.
	 */
	@Test(enabled = true)
	public void testDescribeDatabases() {
		String dbName = "unittest_" + System.currentTimeMillis();
		this.influxDB.createDatabase(dbName);
		this.influxDB.describeDatabases();
		List<String> result = this.influxDB.describeDatabases();
		Assert.assertNotNull(result);
		Assert.assertTrue(result.size() > 0);
		boolean found = false;
		for (String database : result) {
			if (database.equals(dbName)) {
				found = true;
				break;
			}

		}
		Assert.assertTrue(found, "It is expected that describeDataBases contents the newly create database.");
	}

	/**
	 * Test that writing to the new lineprotocol.
	 */
	@Test(enabled = true)
	public void testWrite() {
		String dbName = "write_unittest_" + System.currentTimeMillis();
		this.influxDB.createDatabase(dbName);

		BatchPoints batchPoints = BatchPoints
				.database(dbName)
				.time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
				.tag("async", "true")
				.retentionPolicy("default")
				.build();
		Point point1 = Point.measurement("cpu").field("idle", 90L).field("user", 9L).field("system", 1L).build();
		Point point2 = Point.measurement("disk").field("used", 80L).field("free", 1L).build();
		batchPoints.point(point1);
		batchPoints.point(point2);
		this.influxDB.write(batchPoints);
		Query query = new Query("SELECT idle FROM cpu", dbName);
		this.influxDB.query(query);
		this.influxDB.deleteDatabase(dbName);
	}
}