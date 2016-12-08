/*******************************************************************************
 * Copyright 2011 Krzysztof Otrebski
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package pl.otros.logview.api.io;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.testng.Assert;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import pl.otros.logview.api.InitializationException;
import pl.otros.logview.api.importer.LogImporter;
import pl.otros.logview.api.model.LogData;
import pl.otros.logview.api.model.LogDataCollector;
import pl.otros.logview.api.parser.ParsingContext;
import pl.otros.logview.api.reader.ProxyLogDataCollector;
import pl.otros.logview.importer.DetectOnTheFlyLogImporter;
import pl.otros.logview.importer.UtilLoggingXmlLogImporter;
import utils.LogImporterAndFile;
import utils.TestingUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.SequenceInputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UtilsTest {

  public static final int PORT = 45501;
  private static final String HTTP_GZIPPED = "http://127.0.0.1:" + PORT + "/log.txt.gz";
  private static final String HTTP_NOT_GZIPPED = "http://127.0.0.1:" + PORT + "/log.txt";
  public static FileSystemManager fsManager;
  private static WireMockServer wireMock;

  @BeforeClass
  public static void setUp() throws IOException {
    fsManager = VFS.getManager();
    System.out.println("Starting wiremock");
    wireMock = new WireMockServer(wireMockConfig().port(PORT));
    final byte[] gzipped = IOUtils.toByteArray(UtilsTest.class.getClassLoader().getResourceAsStream("hierarchy/hierarchy.log.gz"));
    final byte[] notGzipped = IOUtils.toByteArray(UtilsTest.class.getClassLoader().getResourceAsStream("hierarchy/hierarchy.log"));

    wireMock.stubFor(get(urlEqualTo("/log.txt"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "text/plain")
            .withBody(notGzipped)));

    wireMock.stubFor(get(urlEqualTo("/log.txt.gz"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "text/plain")
            .withBody(gzipped)));

    wireMock.start();

  }

  @AfterClass
  public static void tearDown() {
    wireMock.shutdown();
  }

  @Test
  public void testEmptyFile() throws IOException {
    FileObject resolveFile = resolveFileObject("/empty.log");
    AssertJUnit.assertEquals(0, resolveFile.getContent().getSize());
    boolean checkIfIsGzipped = Utils.checkIfIsGzipped(resolveFile);
    AssertJUnit.assertFalse(checkIfIsGzipped);
  }

  @Test
  public void testEmptyGzipedFile() throws IOException {
    FileObject resolveFile = resolveFileObject("/empty.log.gz");
    AssertJUnit.assertEquals(26, resolveFile.getContent().getSize());
    boolean checkIfIsGzipped = Utils.checkIfIsGzipped(resolveFile);
    AssertJUnit.assertTrue(checkIfIsGzipped);
  }

  @Test
  public void testGzipedFile() throws IOException {
    FileObject resolveFile = resolveFileObject("/jul_log.txt.gz");
    boolean checkIfIsGzipped = Utils.checkIfIsGzipped(resolveFile);
    AssertJUnit.assertTrue(resolveFile.getName() + " should be compressed",
        checkIfIsGzipped);
  }

  @Test
  public void testNotGzipedFile() throws IOException {
    FileObject resolveFile = resolveFileObject("/jul_log.txt");
    boolean checkIfIsGzipped = Utils.checkIfIsGzipped(resolveFile);
    AssertJUnit.assertFalse(resolveFile.getName()
        + " should be not compressed", checkIfIsGzipped);
  }

  @Test
  public void testSmallGzipedFile() throws IOException {
    FileObject resolveFile = resolveFileObject("/smallFile.txt.gz");
    boolean checkIfIsGzipped = Utils.checkIfIsGzipped(resolveFile);
    AssertJUnit.assertTrue(resolveFile.getName() + " should be compressed",
        checkIfIsGzipped);
  }

  private FileObject resolveFileObject(String resources)
      throws FileSystemException {
    URL resource = this.getClass().getResource(resources);
    return fsManager.resolveFile(resource.toExternalForm());
  }

  @Test
  public void testLoadHttpNotGzipped() throws Exception {
    String url = HTTP_NOT_GZIPPED;
    LoadingInfo loadingInfo = Utils.openFileObject(fsManager
        .resolveFile(url));
    InputStream contentInputStream = loadingInfo.getContentInputStream();
    byte[] actual = IOUtils.toByteArray(contentInputStream);
    byte[] expected = IOUtils.toByteArray(fsManager.resolveFile(url)
        .getContent().getInputStream());
    // byte[] expected = IOUtils.toByteArray(new URL(url).openStream());

    AssertJUnit.assertEquals(expected.length, actual.length);
    AssertJUnit.assertArrayEquals(expected, actual);

  }

  @Test
  public void testLoadHttpNotGzippedBufferedReader() throws Exception {
    String url = HTTP_NOT_GZIPPED;
    LoadingInfo loadingInfo = Utils.openFileObject(fsManager
        .resolveFile(url));
    InputStream contentInputStream = loadingInfo.getContentInputStream();
    // byte[] expectedBytes =
    // IOUtils.toByteArray(fsManager.resolveFile(url).getContent().getInputStream());

    LineNumberReader bin = new LineNumberReader(new InputStreamReader(
        contentInputStream));

    int lines = 0;
    while (bin.readLine() != null) {
      lines++;
    }

    AssertJUnit.assertEquals(2600, lines);
    // assertEquals(expected.length, actual.length);
    // assertArrayEquals(expected, actual);

  }

  @Test
  public void testLoadHttpGzipped() throws Exception {
    String url = HTTP_GZIPPED;
    LoadingInfo loadingInfo = Utils.openFileObject(fsManager
        .resolveFile(url));
    InputStream contentInputStream = loadingInfo.getContentInputStream();
    byte[] actual = IOUtils.toByteArray(contentInputStream);
    byte[] expected = IOUtils.toByteArray(new GZIPInputStream(new URL(url)
        .openStream()));

    AssertJUnit.assertEquals(expected.length, actual.length);
    // assertArrayEquals(expected, actual);
  }

  @Test
  public void testLoadLocalNotGzipped() throws Exception {
    FileObject fileObject = resolveFileObject("/hierarchy/hierarchy.log");
    LoadingInfo loadingInfo = Utils.openFileObject(fileObject);
    InputStream contentInputStream = loadingInfo.getContentInputStream();
    byte[] actual = IOUtils.toByteArray(contentInputStream);
    byte[] expected = IOUtils.toByteArray(fileObject.getContent()
        .getInputStream());

    AssertJUnit.assertFalse(loadingInfo.isGziped());
    AssertJUnit.assertEquals(expected.length, actual.length);
    // assertArrayEquals(expected, actual);
  }

  @Test
  public void testLoadLocalGzipped() throws Exception {
    FileObject fileObject = resolveFileObject("/hierarchy/hierarchy.log.gz");
    LoadingInfo loadingInfo = Utils.openFileObject(fileObject);
    InputStream contentInputStream = loadingInfo.getContentInputStream();
    byte[] actual = IOUtils.toByteArray(contentInputStream);
    byte[] expected = IOUtils.toByteArray(new GZIPInputStream(fileObject
        .getContent().getInputStream()));

    AssertJUnit.assertTrue(loadingInfo.isGziped());
    AssertJUnit.assertArrayEquals(expected, actual);

  }

  @Test
  public void testSequeceRead() throws Exception {
    String url = HTTP_NOT_GZIPPED;
    FileObject resolveFile = fsManager.resolveFile(url);
    InputStream httpInputStream = resolveFile.getContent().getInputStream();
    byte[] buff = Utils.loadProbe(httpInputStream, 10000);
    // int read = httpInputStream.read(buff);

    ByteArrayInputStream bin = new ByteArrayInputStream(buff);

    SequenceInputStream sequenceInputStream = new SequenceInputStream(bin,
        httpInputStream);

    byte[] byteArray = IOUtils.toByteArray(new ObservableInputStreamImpl(
        sequenceInputStream));

    LoadingInfo loadingInfo = Utils.openFileObject(
        fsManager.resolveFile(url), false);
    byte[] byteArrayUtils = IOUtils.toByteArray(loadingInfo
        .getContentInputStream());
    AssertJUnit.assertEquals(byteArrayUtils.length, byteArray.length);
  }

  @Test
  public void testSequeceReadGzipped() throws Exception {
    String url = HTTP_GZIPPED;
    FileObject resolveFile = fsManager.resolveFile(url);
    InputStream httpInputStream = resolveFile.getContent().getInputStream();
    byte[] buff = Utils.loadProbe(httpInputStream, 10000);
    // int read = httpInputStream.read(buff);

    ByteArrayInputStream bin = new ByteArrayInputStream(buff);

    SequenceInputStream sequenceInputStream = new SequenceInputStream(bin,
        httpInputStream);

    byte[] byteArray = IOUtils.toByteArray(new GZIPInputStream(
        new ObservableInputStreamImpl(sequenceInputStream)));

    LoadingInfo loadingInfo = Utils.openFileObject(
        fsManager.resolveFile(url), false);
    byte[] byteArrayUtils = IOUtils.toByteArray(loadingInfo
        .getContentInputStream());
    AssertJUnit.assertEquals(byteArrayUtils.length, byteArray.length);
  }

  @Test
  public void testLoadingLog() throws Exception {
    LoadingInfo loadingInfo = Utils.openFileObject(
        fsManager.resolveFile(HTTP_GZIPPED), false);
    LogImporter importer = new UtilLoggingXmlLogImporter();
    importer.init(new Properties());
    ParsingContext parsingContext = new ParsingContext("");
    importer.initParsingContext(parsingContext);
    ProxyLogDataCollector proxyLogDataCollector = new ProxyLogDataCollector();

    importer.importLogs(loadingInfo.getContentInputStream(),
        proxyLogDataCollector, parsingContext);

    LogData[] logData = proxyLogDataCollector.getLogData();
    AssertJUnit.assertEquals(236, logData.length);

  }

  @Test
  public void getFileObjectShortNameIp() throws Exception {
    String scheme = "sftp";
    String url = "sftp://10.0.22.3/logs/out.log";
    String baseName = "out.log";
    String output = "sftp://10.0.22.3/out.log";

    testGetObjectShortName(scheme, url, baseName, output);
  }

  @Test
  public void getFileObjectShortNameLongHost() throws Exception {
    String scheme = "sftp";
    String url = "sftp://machine.a.b.com/logs/out.log";
    String baseName = "out.log";
    String output = "sftp://machine/out.log";

    testGetObjectShortName(scheme, url, baseName, output);
  }

  @Test
  public void getFileObjectShortNameShortHost() throws Exception {
    String scheme = "sftp";
    String url = "sftp://machine/logs/out.log";
    String baseName = "out.log";
    String output = "sftp://machine/out.log";

    testGetObjectShortName(scheme, url, baseName, output);
  }

  @Test
  public void getFileObjectShortNameLocalFile() throws Exception {
    String scheme = "file";
    String url = "file://opt/logs/out.log";
    String baseName = "out.log";
    String output = "file://out.log";

    testGetObjectShortName(scheme, url, baseName, output);
  }

  @Test
  private void testGetObjectShortName(String scheme, String url,
                                      String baseName, String output) {
    // given
    FileObject fileObjectMock = mock(FileObject.class);
    FileName fileNameMock = mock(FileName.class);

    when(fileObjectMock.getName()).thenReturn(fileNameMock);
    when(fileNameMock.getScheme()).thenReturn(scheme);
    when(fileNameMock.getURI()).thenReturn(url);
    when(fileNameMock.getBaseName()).thenReturn(baseName);

    // when
    String fileObjectShortName = Utils
        .getFileObjectShortName(fileObjectMock);

    // then
    AssertJUnit.assertEquals(output, fileObjectShortName);
  }

  @Test
  public void testLoadProbeEmpty() throws IOException {
    // given
    // when
    byte[] loadProbe = Utils.loadProbe(
        new ByteArrayInputStream(new byte[0]), 1024);

    // then
    AssertJUnit.assertEquals(0, loadProbe.length);
  }


  @Test
  public void testDetectLogEventStartFromNewLines() throws IOException, InitializationException {
    //given
    final LogImporterAndFile importerAndFile = TestingUtils.log4jPatternImporterAndFile();
    final String logFile = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream(importerAndFile.getFile()), "UTF-8");
    final LogImporter logImporter = importerAndFile.getLogImporter();
    final List<Long> expected = Arrays.asList(44L, 94L, 110L, 160L, 182L, 248L, 249L, 299L, 349L, 399L, 450L, 500L, 550L, 601L, 651L);

    //when

    final List<Long> positions = Utils.detectLogEventStart(logFile, logImporter);

    //then
    Assert.assertEquals(positions, expected);
  }

  @Test
  public void testDetectLogEventStartFromNewCustomPositions() throws IOException, InitializationException {
    //given
    final String logFile = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("log4j.txt"), "UTF-8");
    final LogImporter logImporter = new LogImporterStartingWithT();
    final List<Long> expected = Arrays.asList(31L, 75L, 141L, 213L, 280L, 330L, 380L, 425L, 431L, 481L, 531L, 581L, 631L, 681L);

    //when
    final List<Long> positions = Utils.detectLogEventStart(logFile, logImporter);

    //then
    Assert.assertEquals(positions, expected);
  }


  private static class LogImporterStartingWithT extends DetectOnTheFlyLogImporter {

    public LogImporterStartingWithT() {
      super(Collections.emptyList());
    }

    @Override
    public void importLogs(InputStream in, LogDataCollector dataCollector, ParsingContext parsingContext) {
      try {
        final String string = IOUtils.toString(in, "UTF-8");
        if (string.startsWith("T")) {
          final String replaceAll = string.replaceAll("[^T]*", "");
          final int count = replaceAll.length();
          IntStream.range(0, count).forEach(i -> dataCollector.add(new LogData()));
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

  }
}
