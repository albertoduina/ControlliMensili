package contMensili;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.io.Opener;
import ij.text.TextWindow;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import utils.InputOutput;
import utils.MyConst;
import utils.MyLog;
import utils.ReadDicom;
import utils.TableSequence;
import utils.TableUtils;
import utils.UtilAyv;

public class Sequenze_Test {

	public static String[][] unOrderedTable = null;
	public static String[][] orderedTable = null;
	public static String[][] codeTable = null;
	public static String dirLocation = "";
	public static boolean mark = false;

	@Before
	public void setUp() throws Exception {

		if (mark)
			IJ.log(">001");
		new ImageJ(ImageJ.NORMAL);

		String path10 = InputOutput.findResource("vet14_sequenze.txt");
		unOrderedTable = InputOutput.readStringMatrixFromFile(path10);

		String path11 = InputOutput.findResource("vet15_sequenze.txt");
		orderedTable = InputOutput.readStringMatrixFromFile(path11);

		String path12 = InputOutput.findResource("vet16_sequenze.txt");
		codeTable = InputOutput.readStringMatrixFromFile(path12);

		// generazione di una directory vuota
		// NB: per generare qualcosa nella /bin noi dobbiamo lavorare sulla
		// ./data
		String dir2Test = "C://Dati/vuota/";
		if (InputOutput.checkDir(dir2Test)) {
			boolean success3 = InputOutput.deleteDir(new File(dir2Test));
			assertTrue("fallita cancellazione directory", success3);
		}
		boolean success4 = InputOutput.createDir(new File(dir2Test));
		assertTrue("fallita creazione directory " + dir2Test, success4);
		assertTrue("verificata presenza directory",
				InputOutput.checkDir(dir2Test));
		// -----

		String dirTest = "/test3/";

		if (InputOutput.checkDir(dirTest)) {
			boolean success1 = InputOutput.deleteDir(new File(dirTest));
			if (success1)
				assertTrue("fallita cancellazione directory", success1);
		}

		boolean success2 = InputOutput.createDir(new File(dirTest));
		assertTrue("fallita creazione directory", success2);
		assertTrue("verificata presenza directory",
				InputOutput.checkDir(dirTest));

		//
		String[] listFiles = { "BTMA_01testP6", "BTMA_02testP6",
				"BTMA_03testP6", "BTMA_04testP6", "BTMA_05testP6",
				"BTMA_06testP6" };

		String[] path1 = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, listFiles, dirTest);

		// MyLog.logVector(path1, "path1");

		dirLocation = new File(path1[0]).getParent();
		// IJ.log("dirLocation=" + dirLocation);
		// IJ.log("terminato @before felicemente");
		if (mark)
			IJ.log("001>");
	}

	@After
	public void tearDown() throws Exception {
		// String dirTest = "/test3/";
		//
		// if (InputOutput.checkDir(dirTest)) {
		// boolean success1 = InputOutput.deleteDir(new File(dirTest));
		// assertTrue("fallita cancellazione directory", success1);
		// }
	}

	@Test
	public final void testLogVerifySequenceTableLONG() {

		if (mark)
			IJ.log(">002");

		String[][] expectTable = InputOutput
				.readStringMatrixFromFile("./data/vet18.txt");

		// TableUtils.dumpTable(expectTable, "expectTable");

		boolean test = true;
		new Sequenze_().logVerifySequenceTable(expectTable, test);

		// EXPECTED OUTPUT in the Log file:
		// Problemi con le immagini, vedere il log
		// ---------------------------------------------
		// per il codice BUSS_ sono necessarie 2 immagini, trovate 1
		// ./test3/BUSS_05testP6 ser:29 acq:1 ima:5 coil:BC
		// ---------------------------------------------
		if (mark)
			IJ.log("002>");

	}

	@Test
	public final void testLogVerifyList2() {
		if (mark)
			IJ.log(">003");

		String[][] expectTable = { { "BUSS_", "2", "1", "/test3/BUSS_05testP6",
				"29", "1", "5", "BC" } };

		new Sequenze_().logVerifySequenceTable(expectTable, true);

		// EXPECTED OUTPUT in the Log file:
		// Problemi con le immagini, vedere il log
		// ---------------------------------------------
		// per il codice BUSS_ sono necessarie 2 immagini, trovate 1
		// ./test3/BUSS_05testP6 ser:29 acq:1 ima:5 coil:BC
		// ---------------------------------------------
		if (mark)
			IJ.log("003>");

	}

	@Test
	public final void testGetFileListing() {
		if (mark)
			IJ.log(">004");

		String startingDir = "C://Dati/vuota/";
		List<File> listona = new Sequenze_().getFileListing(new File(
				startingDir));
		for (File file : listona) {
			System.out.println(file);
		}
		if (mark)
			IJ.log("004>");

	}

	// @Test
	// public final void testLoadList2() {
	//
	// // String CODE_FILE = "codici.txt";
	// String CODE_FILE = "codiciNew.csv";
	// String EXPAND_FILE = "expand.csv";
	//
	// ArrayList<ArrayList<String>> tabella1 = new InputOutput()
	// .readFile3(CODE_FILE);
	// String[][] tableCode = new InputOutput()
	// .fromArrayListToStringTable(tabella1);
	//
	// ArrayList<ArrayList<String>> tabella2 = new InputOutput()
	// .readFile3(EXPAND_FILE);
	// String[][] tableExpand = new InputOutput()
	// .fromArrayListToStringTable(tabella2);
	// List<File> result = new Sequenze_()
	// .getFileListing(new File(dirLocation));
	// String[] strList1 = new String[result.size()];
	// int j1 = 0;
	// for (File file : result) {
	// strList1[j1++] = file.getPath();
	// }
	//
	// // il problema è che quando utilizzo generateSequenceTable il path
	// // indicato per le immagini di test è assoluto. Ciò è usato solo per
	// // questo test in junit ma cozza con la portabilità (per esempip col
	// // calcolatore di casa), pertanto la tabella di uscita andrà trasformata
	// // con il path relativo, in modo che sia portatile
	//
	// String[][] res1 = new Sequenze_().generateSequenceTable(strList1,
	// tableCode, tableExpand);
	// if (res1 == null)
	// MyLog.waitHere("res1==null");
	//
	// MyLog.waitHere("res1.length= " + res1.length);
	//
	// // verifica risultati
	// String[][] expected = InputOutput
	// .readStringMatrixFromFile("./data/vet17.txt");
	// for (int i1 = 0; i1 < res1.length; i1++) {
	// String aux = res1[i1][1];
	// res1[i1][1] = InputOutput.absoluteToRelative(aux);
	// }
	//
	// // MyLog.logMatrix(res1, "res1");
	// // MyLog.logMatrix(expected, "expected");
	//
	// for (int i1 = 0; i1 < res1.length; i1++) {
	// assertTrue(UtilAyv.compareVectors(res1[i1], expected[i1],
	// "errore in riga " + i1));
	// }
	// }

	// @Test
	// public final void testLoadListNull() {
	// // in questo caso ho bisogno di una directory vuota, per cui cancello e
	// // rigenero la /test3
	// String dirTest = "/test3/";
	// if (InputOutput.checkDir(dirTest)) {
	// boolean success3 = InputOutput.deleteDir(new File(dirTest));
	// assertTrue("fallita cancellazione directory", success3);
	// }
	// boolean success4 = InputOutput.createDir(new File(dirTest));
	// assertTrue("fallita creazione directory", success4);
	// assertTrue("verificata presenza directory",
	// InputOutput.checkDir(dirTest));
	// String CODE_FILE = "/codici.txt";
	// int CODE_COLUMNS = 4;
	// String EXPAND_FILE = "/expand.txt";
	// int EXPAND_COLUMNS = 6;
	// // carico da file la tabella codici
	// String[][] tableCode = new InputOutput().readFile1(CODE_FILE,
	// CODE_COLUMNS);
	// String[][] tableExpand = new InputOutput().readFile1(EXPAND_FILE,
	// EXPAND_COLUMNS);
	// // creo la lista utilizzando loadPath
	// List<File> result = new Sequenze_().getFileListing(new File(dirTest));
	// String[] strList = new String[result.size()];
	// int j1 = 0;
	// for (File file : result) {
	// strList[j1++] = file.getPath();
	// }
	// // testo loadList2
	// String[][] res1 = new Sequenze_().generateSequenceTable(null,
	// tableCode, tableExpand);
	// // verifica risultati
	// assertNull(res1);
	// String[][] res2 = new Sequenze_().generateSequenceTable(strList, null,
	// null);
	// // verifica risultati
	// assertNull(res2);
	// }

	@Test
	public final void testDuplicateTable() {
		if (mark)
			IJ.log(">005");

		String[][] inTable = { { "aa11", "bb11", "cc11" },
				{ "aa22", "bb22", "cc22" } };
		String[][] outTable = new TableUtils().duplicateTable(inTable);
		assertTrue(TableUtils.compareTable(inTable, outTable));
		if (mark)
			IJ.log("005>");

	}

	// @Test
	// public final void testDuplicateTableFault() {
	// String[][] inTable = { { "aa11", "bb11", "cc11" },
	// { "aa22", "bb22", "cc22" } };
	//
	// String[][] outTable = new TableUtils().duplicateTable(inTable);
	// outTable[0][0] = "pp";
	// assertFalse(TableUtils.compareTable(inTable, outTable));
	// }

	// @Test
	// public final void testBubbleSortSequenceTable() {
	//
	// String[][] outTable = new Sequenze_()
	// .bubbleSortSequenceTable(unOrderedTable);
	// TableUtils.dumpTable(outTable, "outTable");
	// TableUtils.dumpTable(orderedTable, "orderedTable");
	// MyLog.waitHere();
	// assertTrue(TableUtils.compareTable(orderedTable, outTable));
	// }

	@Test
	public final void testCallPluginFromSequenceTable() {
		if (mark)
			IJ.log(">006");

		// 18 dec 2011 sistemato, ora funziona in automatico

		String[][] expected = { { "contMensili.p3rmn_", "#0#1" },
				{ "contMensili.p3rmn_", "#2#3" },
				{ "contMensili.p3rmn_", "#4#5" } };

		// MyLog.logMatrix(codeTable, "codeTable");

		String path10 = new InputOutput().findResource("iw2ayv_sequenze.txt");
		// MyLog.waitHere("path10= " + path10);

		String[][] iw2ayvTable = new TableSequence().loadTable(path10);

		// MyLog.logMatrix(iw2ayvTable, "iw2ayvTable");
		// MyLog.logMatrix(codeTable, "codeTable");
		// MyLog.waitHere();

		boolean test = true;
		boolean superficiali = false;
		boolean p10p11 = true;

		TextWindow tw = new TextWindow("Sequenze", "Sequenze", 200, 200);

		String[][] chiamate = new Sequenze_().callPluginsFromSequenceTable(
				iw2ayvTable, codeTable, test, superficiali, p10p11, tw);

		// TableUtils.dumpTable(expected, "expected");
		// TableUtils.dumpTable(chiamate, "chiamate");
		assertTrue(TableUtils.compareTable(expected, chiamate));
		if (mark)
			IJ.log("006>");

	}

	@Test
	public final void testPluginToBeCalled() {
		if (mark)
			IJ.log(">007");

		String path1 = new InputOutput().findResource("iw2ayv_sequenze.txt");
		String[][] iw2ayvTable = new TableSequence().loadTable(path1);

		// MyLog.logMatrix(iw2ayvTable, "iw2ayvTable");
		// MyLog.logMatrix(codeTable, "codeTable");
		// MyLog.waitHere();

		String nome = new Sequenze_().pluginToBeCalled(2, iw2ayvTable,
				codeTable);
		// MyLog.waitHere("nome= "+nome);
		// System.out.printf("\nnome= " + nome);
		assertEquals("contMensili.p3rmn_", nome);
		if (mark)
			IJ.log("007>");

	}

	@Test
	public final void testPluginToBeCalledWithCoil() {
		if (mark)
			IJ.log(">008");

		String[][] iw2ayvTable = new TableSequence()
				.loadTable(new InputOutput()
						.findResource("iw2ayv_sequenze.txt"));
		String nome = new Sequenze_().pluginToBeCalledWithCoil(2, iw2ayvTable,
				codeTable);
		assertEquals("contMensili.p3rmn_", nome);
		if (mark)
			IJ.log("008>");

	}

	@Test
	public final void testArgumentForPluginToBeCalled2() {
		if (mark)
			IJ.log(">009");

		String argomento = new Sequenze_().argumentForPluginToBeCalled(2,
				orderedTable);
		assertEquals("#2#3", argomento);
		if (mark)
			IJ.log("009>");

	}

	@Test
	public final void testArgumentForPluginToBeCalled4() {
		if (mark)
			IJ.log(">010");

		String argomento = new Sequenze_().argumentForPluginToBeCalled(4,
				orderedTable);
		assertEquals("#4#5#6#7", argomento);
		if (mark)
			IJ.log("010>");

	}

	@Test
	public final void testArgumentForPluginToBeCalled1() {
		if (mark)
			IJ.log(">011");

		String argomento = new Sequenze_().argumentForPluginToBeCalled(0,
				orderedTable);
		assertEquals("#0", argomento);
		if (mark)
			IJ.log("011>");

	}

	@Test
	public final void testExpandCode() {
		if (mark)
			IJ.log(">012");

		String codice = "T2MA_";
		String eco = "180";
		String[][] expandTable = {
				{ "T2MA_", "45", "T2M1_", "1", "0", "p9rmn_" },
				{ "T2MA_", "90", "T2M2_", "1", "0", "p9rmn_" },
				{ "T2MA_", "180", "T2M3_", "1", "0", "p9rmn_" },
				{ "B080_", "20", "C080_", "1", "0", "p9rmn_" },
				{ "T2MA2", "45", "T2M12", "1", "0", "p9rmn_" },
				{ "T2MA2", "90", "T2M22", "1", "0", "p9rmn_" },
				{ "T2MA2", "180", "T2M32", "1", "0", "p9rmn_" },
				{ "T2MA3", "30", "T2M13", "1", "0", "p9rmn_" },
				{ "T2MA3", "60", "T2M23", "1", "0", "p9rmn_" },
				{ "T2MA3", "120", "T2M33", "1", "0", "p9rmn_" } };

		String[] expected = { "T2MA_", "180", "T2M3_", "1" };

		String[] vetExpanded = new Sequenze_().expandCode(codice, eco,
				expandTable);
		new UtilAyv();
		assertTrue(UtilAyv.compareVectors(vetExpanded, expected,
				"errore comparazione"));
		if (mark)
			IJ.log("012>");

	}

	@Test
	public final void testVerifySequenceTable() {
		if (mark)
			IJ.log(">013");

		String[][] outTable = new Sequenze_().verifySequenceTable(orderedTable,
				codeTable);

		String[][] verifyTableExpected = {

				{ "BTMA_", "15", "6", "./test3/BTMA_01testP6", "1", "1",
						"102922955019", "BC" },
				{ "BTMA_", "15", "6", "./test3/BTMA_03testP6", "1", "3",
						"102923022490", "BC" },
				{ "BTMA_", "15", "6", "./test3/BTMA_04testP6", "1", "4",
						"102923555016", "BC" },
				{ "BTMA_", "15", "6", "./test3/BTMA_04testP6", "1", "4",
						"102923555016", "BC" },
				{ "BTMA_", "15", "6", "./test3/BTMA_04testP6", "1", "4",
						"102923555016", "BC" },
				{ "BTMA_", "15", "6", "./test3/BTMA_04testP6", "1", "4",
						"102923555016", "BC" } };
		// TableUtils.dumpTable(orderedTable, "orderedTable");
		// TableUtils.dumpTable(outTable, "outTable");
		// TableUtils.dumpTable(verifyTableExpected, "expectTable");

		assertTrue(TableUtils.compareTable(verifyTableExpected, outTable));
		if (mark)
			IJ.log("013>");
		if (mark)
			MyLog.waitHere();

	}

	@Test
	public final void testReorderSequenceTable() {
		if (mark)
			IJ.log(">014");

		String[][] inTable = {
				{ "0", "./test3/BTMA_01testP6", "BTMA_", "COIL", "15", "29",
						"1", "1", "102922955019", "20", "1" },
				{ "1", "./test3/BTMA_03testP6", "BTMA_", "COIL", "15", "29",
						"1", "3", "102923022490", "20", "1" },
				{ "2", "./test3/BUSS_05testP6", "BUSS_", "COIL", "2", "29",
						"1", "5", "102923090001", "20", "0" },
				{ "3", "./test3/BUSC_02testP6", "BUSC_", "COIL", "2", "29",
						"1", "2", "102923489999", "20", "0" },
				{ "4", "./test3/BTMA_04testP6", "BTMA_", "COIL", "15", "29",
						"1", "4", "102923555016", "20", "0" },
				{ "5", "./test3/BTMA_06testP6", "BTMA_", "COIL", "15", "29",
						"1", "6", "102923622487", "20", "0" } };
		Sequenze_ seq = new Sequenze_();
		String[][] outTable = seq.reorderSequenceTable(inTable, codeTable);

		String[][] expectTable = {
				{ "0", "./test3/BUSS_05testP6", "BUSS_", "COIL", "2", "29",
						"1", "5", "102923090001", "20", "0" },
				{ "1", "./test3/BUSC_02testP6", "BUSC_", "COIL", "2", "29",
						"1", "2", "102923489999", "20", "0" },
				{ "2", "./test3/BTMA_01testP6", "BTMA_", "COIL", "15", "29",
						"1", "1", "102922955019", "20", "1" },
				{ "3", "./test3/BTMA_03testP6", "BTMA_", "COIL", "15", "29",
						"1", "3", "102923022490", "20", "1" },
				{ "4", "./test3/BTMA_04testP6", "BTMA_", "COIL", "15", "29",
						"1", "4", "102923555016", "20", "0" },
				{ "5", "./test3/BTMA_06testP6", "BTMA_", "COIL", "15", "29",
						"1", "6", "102923622487", "20", "0" } };
		// TableUtils.dumpTable(outTable, "outTable");

		assertTrue(TableUtils.compareTable(expectTable, outTable));
		if (mark)
			IJ.log(">014");

	}

	@Test
	public final void testCompareAcqReqOK() {
		if (mark)
			IJ.log(">015");

		String codeImaAcq = "R12F_";
		String codeImaReq = "R12F_";
		String coilImaAcq = "A3B";
		String coilImaReq = "A3B";
		boolean res = new Sequenze_().compareAcqReq(codeImaAcq, codeImaReq,
				coilImaAcq, coilImaReq);
		assertTrue(res);
		if (mark)
			IJ.log("015>");

	}

	@Test
	public final void testCompareAcqReqXXX() {
		if (mark)
			IJ.log(">016");

		String codeImaAcq = "BUSA_";
		String codeImaReq = "BUSA_";
		String coilImaAcq = "C:BC";
		String coilImaReq = "xxx";
		boolean res = new Sequenze_().compareAcqReq(codeImaAcq, codeImaReq,
				coilImaAcq, coilImaReq);
		assertTrue(res);
		if (mark)
			IJ.log("016>");
	}

	@Test
	public final void testCoilPresent() {
		if (mark)
			IJ.log(">017");

		String[] allCoils = { "H31", "H11", "H21" };
		String coilImaReq = "H11";
		boolean res = new Sequenze_().coilPresent(allCoils, coilImaReq);
		assertTrue(res);
		if (mark)
			IJ.log("017>");

	}

	@Test
	public final void testCoilNOTPresent() {
		if (mark)
			IJ.log(">018");

		String[] allCoils = { "H31", "H12", "H21" };
		String coilImaReq = "H11";
		boolean res = new Sequenze_().coilPresent(allCoils, coilImaReq);
		assertFalse(res);
		if (mark)
			IJ.log("018>");

	}

	@Test
	public final void testKludge() {
		if (mark)
			IJ.log(">019");

		String codeIma = "./test2/S12S_MISSING";
		String coil = new UtilAyv().kludge(codeIma);
		assertTrue(coil.equals("C:SP1,2"));
		if (mark)
			IJ.log("019>");

	}
}
