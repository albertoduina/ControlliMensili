package contMensili;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.ImageWindow;
import ij.gui.OvalRoi;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.ContrastEnhancer;
import ij.plugin.PlugIn;

import java.awt.Color;
import java.awt.Polygon;
import java.util.StringTokenizer;

import utils.AboutBox;
import utils.ButtonMessages;
import utils.CustomCanvasGeneric;
import utils.InputOutput;
import utils.MyLog;
import utils.MyMsg;
import utils.MyConst;
import utils.MyVersionUtils;
import utils.ReadDicom;
import utils.ReportStandardInfo;
import utils.TableCode;
import utils.TableExpand;
import utils.TableSequence;
import utils.UtilAyv;

/*
 * Copyright (C) 2007 Alberto Duina, SPEDALI CIVILI DI BRESCIA, Brescia ITALY
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

/**
 * Calcolo Distorsione Geometrica Percentuale
 * 
 * Per salvare i dati in formato xls necessita di Excel_Writer.jar nella
 * directory plugins
 * 
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 * 
 */

public class p8rmn_ implements PlugIn, Measurements {

	static final int ABORT = 1;

	public static String VERSION = "DGP";

	private static String TYPE = " >> CONTROLLO DGP_____________________";

	// ---------------------------"01234567890123456789012345678901234567890"

	/**
	 * directory dati, dove vengono memorizzati ayv.txt e Results1.xls
	 */
	private static String fileDir = "";

	/**
	 * tabella coi dati di ayv.txt (generati da Sequenze)
	 */

	public void run(String args) {

		UtilAyv.setMyPrecision();

		if (IJ.versionLessThan("1.43k"))
			return;

		//
		// nota bene: le seguenti istruzioni devono essere all'inizio, in questo
		// modo il messaggio viene emesso, altrimenti si ha una eccezione
		//
		try {
			Class.forName("utils.IW2AYV");
		} catch (ClassNotFoundException e) {
			IJ.error("ATTENZIONE, manca il file iw2ayv_xxx.jar");
			return;
		}

		String className = this.getClass().getName();
		String user1 = System.getProperty("user.name");
		TableCode tc1 = new TableCode();
		String iw2ayv1 = tc1.nameTable("codici", "csv");
		TableExpand tc2 = new TableExpand();
		String iw2ayv2 = tc1.nameTable("expand", "csv");

		VERSION = user1 + ":" + className + "build_" + MyVersion.getVersion() + ":iw2ayv_build_"
				+ MyVersionUtils.getVersion() + ":" + iw2ayv1 + ":" + iw2ayv2;

//		VERSION = className + "_build_" + MyVersion.getVersion() + "_iw2ayv_build_" + MyVersionUtils.getVersion();

		fileDir = Prefs.get("prefer.string1", "none");

		// tabl = new InputOutput().readFile1(CODE_FILE, TOKENS4);
		int nTokens = new StringTokenizer(args, "#").countTokens();
		if (nTokens == 0) {
			manualMenu(0, "");
		} else if (nTokens == 1) {
			autoMenu(args);
		} else {
			ButtonMessages.ModelessMsg(VERSION + " >> p8rmn ERRORE PARAMETRI CHIAMATA  <03>", "CHIUDI");
			IJ.log(" p8rmn ERRORE PARAMETRI CHIAMATA =" + args);
			return;
		}
	}

	public int manualMenu(int preset, String testDirectory) {
		boolean retry = false;
		boolean step = false;
		boolean autoCalled = false;
		int riga1 = 0;
		do {

			int userSelection1 = UtilAyv.userSelectionManual(VERSION, TYPE);
			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				retry = false;
				return 0;
			case 2:
				// new AboutBox().about("Controllo DGP", this.getClass());
				new AboutBox().about("Controllo DGP", MyVersion.CURRENT_VERSION);
				retry = true;
				break;
			case 3:
				selfTestMenu();
				retry = true;
				return 1;
			case 4:
				step = true;
			case 5:
				boolean test = false;
				String path1 = UtilAyv.imageSelection("SELEZIONARE IMMAGINE...");
				if (path1 == null)
					return 5;
				mainDgp(path1, riga1, autoCalled, step, test);
				UtilAyv.afterWork();

				retry = true;
			}
		} while (retry);
		new AboutBox().close();
		return 0;
	}

	public int autoMenu(String autoArgs) {
		MyLog.appendLog(fileDir + "MyLog.txt", "p8 riceve " + autoArgs);

		// StringTokenizer st = new StringTokenizer(autoArgs, "#");

		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);
		if (vetRiga[0] == -1) {
			IJ.log("selfTestSilent.p8rmn_");
			selfTestSilent();
			return 0;
		}
		int riga1 = vetRiga[0];

		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir + MyConst.SEQUENZE_FILE);

		boolean retry = false;
		boolean step = false;
		do {
			// int userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE);
			int userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE,
					TableSequence.getCode(iw2ayvTable, vetRiga[0]), TableSequence.getCoil(iw2ayvTable, vetRiga[0]),
					vetRiga[0] + 1, TableSequence.getLength(iw2ayvTable));

			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				return 0;
			case 2:
				// new AboutBox().about("Controllo DGP", this.getClass());
				new AboutBox().about("Controllo DGP", MyVersion.CURRENT_VERSION);
				retry = true;
				break;
			case 3:
				step = true;
			case 4:
				boolean autoCalled = true;
				boolean test = false;

				String path1 = TableSequence.getPath(iw2ayvTable, riga1);
				ResultsTable rt = mainDgp(path1, riga1, autoCalled, step, test);
				
				if (rt == null) {
					TableCode tc11 = new TableCode();
					String[][] tabCodici11 = tc11.loadMultipleTable("codici", ".csv");
					ImagePlus imp11 = UtilAyv.openImageNoDisplay(path1, true);
					String[] info11 = ReportStandardInfo.getSimpleStandardInfo(path1, imp11, tabCodici11, VERSION,
							autoCalled);
					String slicePos = ReadDicom
							.readSubstring(ReadDicom.readDicomParameter(imp11, MyConst.DICOM_IMAGE_POSITION), 3);
					rt = ReportStandardInfo.abortResultTable_P8(info11, slicePos);
				}

				UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, rt);

				retry = false;
				break;
			}
		} while (retry);
		new AboutBox().close();
		UtilAyv.afterWork();
		return riga1;
	}

	@SuppressWarnings("deprecation")
	public ResultsTable mainDgp(String path1, int riga1, boolean autoCalled, boolean step, boolean test) {
		boolean accetta = false;
		ResultsTable rt = null;
		ResultsTable rt11 = null;

		UtilAyv.setMeasure(MEAN + STD_DEV);
		// String[][] tabCodici = new InputOutput().readFile1(MyConst.CODE_FILE,
		// MyConst.P8_TOKENS4);

		// if (autoCalled) {
		// String[][] tabCodici = TableCode
		// .loadMultipleTable(MyConst.CODE_GROUP);
		//
		// String[][] iw2ayvTable = new TableSequence().loadTable(fileDir
		// + MyConst.SEQUENZE_FILE);
		//
		// MyLog.waitHere();
		//
		// }
		//
		// Qui si torna se la misura � da rifare
		//
		do {
			UtilAyv.closeResultsWindow();

			ImagePlus imp1 = UtilAyv.openImageMaximized(path1);

			// 291219 iw2ayv
			new ContrastEnhancer().stretchHistogram(imp1.getProcessor(), 0.5);

			String slicePos = ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_IMAGE_POSITION),
					3);

			overlayNumbers(imp1, true);

			imp1.updateAndDraw();

			double dimPixel = ReadDicom.readDouble(
					ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 1));

			Polygon poli1 = UtilAyv.selectionPointsClick(imp1,
					"Cliccare nell'ordine sui 4 angoli del quadrato, poi premere FINE POSIZIONAMENTO",
					"FINE POSIZIONAMENTO");

			if (poli1 == null)
				return null;

//			{
//				TableCode tc11 = new TableCode();
//				String[][] tabCodici11 = tc11.loadMultipleTable("codici", ".csv");
//					String[] info11 = ReportStandardInfo.getSimpleStandardInfo(path1, imp1, tabCodici11, VERSION,
//						autoCalled);
//				return ReportStandardInfo.abortResultTable_P8(info11, slicePos);
//
//			}

			if (howmanyPoints(poli1) == MyConst.P8_NUM_POINTS4) {
				double[] vetResults = mainCalculation(poli1, dimPixel);

				// Salvataggio dei risultati nella ResultsTable
				int[] xPoints = poli1.xpoints;
				int[] yPoints = poli1.ypoints;
				double dgp1 = vetResults[6];
				double dgp2 = vetResults[7];
				double dgp3 = vetResults[8];
				double dgp4 = vetResults[9];
				double dgp5 = vetResults[10];
				double dgp6 = vetResults[11];

				// String[][] info1 = ReportStandardInfo.getStandardInfo(iw2ayvTable,
				// riga1, tabCodici, VERSION, autoCalled);

				// String[][] tabCodici = TableCode
				// .loadMultipleTable(MyConst.CODE_GROUP);
				TableCode tc1 = new TableCode();
				String[][] tabCodici = tc1.loadMultipleTable("codici", ".csv");

				String[] info1 = ReportStandardInfo.getSimpleStandardInfo(path1, imp1, tabCodici, VERSION, autoCalled);

				// rt = ReportStandardInfo.putStandardInfoRT(info1);
				// int col = 2;
				String t1 = "TESTO";
				String s2 = "VALORE";
				String s3 = "seg_ax";
				String s4 = "seg_ay";
				String s5 = "seg_bx";
				String s6 = "seg_by";

				rt = ReportStandardInfo.putSimpleStandardInfoRT_new(info1);    
				rt.showRowNumbers(true);

				rt.addValue(t1, "slicePos");
				rt.addValue(s2, UtilAyv.convertToDouble(slicePos));

				rt.incrementCounter();
				rt.addValue(t1, "DGP1");
				rt.addValue(s2, dgp1);
				rt.addValue(s3, xPoints[0]);
				rt.addValue(s4, yPoints[0]);
				rt.addValue(s5, xPoints[1]);
				rt.addValue(s6, yPoints[1]);

				rt.incrementCounter();
				rt.addValue(t1, "DGP2");
				rt.addValue(s2, dgp2);
				rt.addValue(s3, xPoints[1]);
				rt.addValue(s4, yPoints[1]);
				rt.addValue(s5, xPoints[2]);
				rt.addValue(s6, yPoints[2]);

				rt.incrementCounter();
				rt.addValue(t1, "DGP3");
				rt.addValue(s2, dgp3);
				rt.addValue(s3, xPoints[2]);
				rt.addValue(s4, yPoints[2]);
				rt.addValue(s5, xPoints[3]);
				rt.addValue(s6, yPoints[3]);

				rt.incrementCounter();
				rt.addValue(t1, "DGP4");
				rt.addValue(s2, dgp4);
				rt.addValue(s3, xPoints[3]);
				rt.addValue(s4, yPoints[3]);
				rt.addValue(s5, xPoints[0]);
				rt.addValue(s6, yPoints[0]);

				rt.incrementCounter();
				rt.addValue(t1, "DGP5");
				rt.addValue(s2, dgp5);
				rt.addValue(s3, xPoints[0]);
				rt.addValue(s4, yPoints[0]);
				rt.addValue(s5, xPoints[2]);
				rt.addValue(s6, yPoints[2]);

				rt.incrementCounter();
				rt.addValue(t1, "DGP6");
				rt.addValue(s2, dgp6);
				rt.addValue(s3, xPoints[1]);
				rt.addValue(s4, yPoints[1]);
				rt.addValue(s5, xPoints[3]);
				rt.addValue(s6, yPoints[3]);

				rt.incrementCounter();
				rt.addValue(t1, "DimPix");
				rt.addValue(s2, dimPixel);

				rt.show("Results");
			} else {
				IJ.beep();
				IJ.showMessage("--- A T T E N Z I O N E ---", "Non sono stati selezionati 4 punti,--- R I F A R E ---");

			}

			if (autoCalled && !test) {
				accetta = MyMsg.accettaMenu();
			} else {
				if (!test) {
					accetta = MyMsg.msgStandalone();
				} else
					accetta = test;
			}

			// if (autoCalled) {
			// int userSelection3 = ButtonMessages.ModelessMsg(
			// "Accettare il risultato delle misure? <06>",
			// "ACCETTA", "RIFAI");
			// switch (userSelection3) {
			// case 1:
			// UtilAyv.cleanUp();
			// accetta = false;
			// break;
			// case 2:
			// accetta = true;
			// // UtilAyv.saveResults2(vetRiga, slicePos, iw2ayvTable)
			// IJ.run("Excel...", "select...=[" + fileDir + XLS_FILE + "]");
			// TableSequence lr = new TableSequence();
			// lr.putDone(strRiga3, riga1);
			// lr.writeTable(fileDir + SEQUENZE_FILE, strRiga3);
			// UtilAyv.cleanUp();
			// break;
			// }
			// } else {
			// ButtonMessages
			// .ModelessMsg(
			// "Fine programma, in modo STAND-ALONE salvare A MANO la finestra Risultati
			// <07>",
			// "CONTINUA");
			// accetta = true;
			// }
		} while (!accetta); // do

		return rt;

		// UtilAyv.resetResultsTable();
		// UtilAyv.resetMeasure(misure1);
		// InputOutput.deleteDir(new File(TEST_DIRECTORY));

	}

	public static double segmentCalculation(double xStart, double yStart, double xEnd, double yEnd, double dimPixel) {
		// segm= sqrt((xStart-xEnd)^2 +(yStart-yEnd)^2)
		double len1 = Math.sqrt(Math.pow((xStart - xEnd), 2.0) + Math.pow((yStart - yEnd), 2.0)) * dimPixel;
		// IJ.log("calcolo segmento xStart= "+xStart+" xEnd= "+xEnd+" yStart= "+yStart+
		// "yEnd= "+yEnd+" dimPixel= "+dimPixel+" lunhghezza= "+len1);
		return len1;
	}

	public static double dgpCalculation(double segmCalc, double segmTeor) {

		double dgp1 = 100 * ((segmCalc - segmTeor) / segmTeor);
		// IJ.log("calcolo DGP segmCalc= "+segmCalc+" segmTeor= "+segmTeor+" dgp=
		// "+dgp1);
		return dgp1;
	}

	public void overlayNumbers(ImagePlus imp1, boolean verbose) {
		// imp1.setRoi(new OvalRoi(0, 0, 0, 0));
		CustomCanvasGeneric ccg1 = new CustomCanvasGeneric(imp1);
		double[] cx1 = { 49, 212, 212, 49 };
		double[] cy1 = { 54, 54, 222, 222 };

		ccg1.setPosition1(cx1, cy1);
		ccg1.setColor1(Color.red);

		if (verbose) {
			ImageWindow iw1 = new ImageWindow(imp1, ccg1);
			iw1.maximize();
		}
	}

	public double[] referenceSiemens() {
		double len1 = 121.875;
		double len2 = 120.3125;
		double len3 = 121.875;
		double len4 = 120.3125;
		double len5 = 171.25598757780705;
		double len6 = 171.25598757780705;

		double dgp1 = 1.5625;
		double dgp2 = 0.26041666666666663;
		double dgp3 = 1.5625;
		double dgp4 = 0.26041666666666663;
		double dgp5 = 0.9169048779063401;
		double dgp6 = 0.9169048779063401;

		double[] vetReference = { len1, len2, len3, len4, len5, len6, dgp1, dgp2, dgp3, dgp4, dgp5, dgp6 };
		return vetReference;
	}

	public double[] referenceGe() {
		double len1 = 120.35461809221613;
		double len2 = 117.99354028571223;
		double len3 = 120.33686686053446;
		double len4 = 118.77464715585896;
		double len5 = 169.0593207325689;
		double len6 = 168.56578444139487;

		double dgp1 = 0.29551507684677364;
		double dgp2 = -1.6720497619064763;
		double dgp3 = 0.2807223837787139;
		double dgp4 = -1.0211273701175354;
		double dgp5 = -0.37753639801478867;
		double dgp6 = -0.6683650905156869;

		double[] vetReference = { len1, len2, len3, len4, len5, len6, dgp1, dgp2, dgp3, dgp4, dgp5, dgp6 };
		return vetReference;
	}

	public boolean verifyResults(double[] vetResults, double[] vetReference, boolean verbose) {
		boolean testok = true;

		for (int i1 = 0; i1 < 6; i1++) {
			if (vetResults[i1] != vetReference[i1]) {
				IJ.log("len" + (i1 + 1) + " ERRATA " + vetResults[i1] + " anzich� " + vetReference[i1]);
				testok = false;
			}
		}
		for (int i1 = 0; i1 < 6; i1++) {
			if (vetResults[i1 + 6] != vetReference[i1 + 6]) {
				IJ.log("dgp" + (i1 + 1) + " ERRATA " + vetResults[i1 + 6] + " anzich� " + vetReference[i1 + 6]);
				testok = false;
			}
		}
		if (verbose) {
			if (testok == true)
				ButtonMessages.ModelessMsg("Fine SelfTest TUTTO OK  <42>", "CONTINUA");
			else
				ButtonMessages.ModelessMsg("Fine SelfTest CON ERRORI  <43>", "CONTINUA");
		}
		return testok;
	}

	void selfTestMenu() {
		if (new InputOutput().checkJar(MyConst.TEST_FILE)) {
			int userSelection2 = UtilAyv.siemensGe();
			switch (userSelection2) {
			case 1: {
				// GE
				String home1 = findTestImages();
				String path1 = home1 + "/HT5A2_testP8";
				int[] vetX = MyConst.P8_X_POINT_TESTGE;
				int[] vetY = MyConst.P8_Y_POINT_TESTGE;
				double[] vetReference = referenceGe();
				testExcecution(path1, vetX, vetY, vetReference, true);
				return;
			}
			case 2:
				// Siemens
				String home1 = findTestImages();
				String path1 = home1 + "/HT2A_testP8";
				int[] vetX = MyConst.P8_X_POINT_TESTSIEMENS;
				int[] vetY = MyConst.P8_Y_POINT_TESTSIEMENS;
				double[] vetReference = referenceSiemens();
				testExcecution(path1, vetX, vetY, vetReference, true);
				return;
			}
		}
		UtilAyv.noTest2();
	}

	void selfTestSilent() {
		if (new InputOutput().checkJar(MyConst.TEST_FILE)) {
			String home1 = findTestImages();
			String path1 = home1 + "/HT2A_testP8";
			int[] vetX = MyConst.P8_X_POINT_TESTSIEMENS;
			int[] vetY = MyConst.P8_Y_POINT_TESTSIEMENS;
			double[] vetReference = referenceSiemens();
			boolean ok = testExcecution(path1, vetX, vetY, vetReference, false);
			if (ok) {
				IJ.log("Il test di p8rmn_ DGP � stato SUPERATO");
			} else {
				IJ.log("Il test di p8rmn_ DGP evidenzia degli ERRORI");
			}
			return;
		} else {
			IJ.log("Non trovato il file Test2.jar indispensabile per il test");
		}
	}

	boolean testExcecution(String path1, int[] vetX, int[] vetY, double[] vetReference, boolean verbose) {

		ImagePlus imp1 = null;
		if (verbose) {
			imp1 = UtilAyv.openImageMaximized(path1);
		} else {
			imp1 = UtilAyv.openImageNoDisplay(path1, false);
		}
		overlayNumbers(imp1, verbose);
		double dimPixel = ReadDicom.readDouble(
				ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 1));

		Polygon poli1 = UtilAyv.clickSimulation(imp1, vetX, vetY);
		int num = howmanyPoints(poli1);
		if (num != 4)
			return false;
		double[] vetResults = mainCalculation(poli1, dimPixel);

		boolean ok = verifyResults(vetResults, vetReference, verbose);
		// if (verbose) {
		// if (ok)
		// ButtonMessages.ModelessMsg("Fine SelfTest TUTTO OK <42>",
		// "CONTINUA");
		// else
		// ButtonMessages.ModelessMsg("Fine SelfTest CON ERRORI <43>",
		// "CONTINUA");
		// }
		// UtilAyv.afterWork();
		return ok;
	}

	double[] mainCalculation(Polygon poli1, double dimPixel) {
		// input check in the calling routine
		int[] xPoints = poli1.xpoints;
		int[] yPoints = poli1.ypoints;
		double len1 = segmentCalculation(xPoints[0], yPoints[0], xPoints[1], yPoints[1], dimPixel);
		double dgp1 = dgpCalculation(len1, MyConst.P8_PHANTOM_SIDE);
		double len2 = segmentCalculation(xPoints[1], yPoints[1], xPoints[2], yPoints[2], dimPixel);
		double dgp2 = dgpCalculation(len2, MyConst.P8_PHANTOM_SIDE);
		double len3 = segmentCalculation(xPoints[2], yPoints[2], xPoints[3], yPoints[3], dimPixel);
		double dgp3 = dgpCalculation(len3, MyConst.P8_PHANTOM_SIDE);
		double len4 = segmentCalculation(xPoints[3], yPoints[3], xPoints[0], yPoints[0], dimPixel);
		double dgp4 = dgpCalculation(len4, MyConst.P8_PHANTOM_SIDE);
		double len5 = segmentCalculation(xPoints[0], yPoints[0], xPoints[2], yPoints[2], dimPixel);
		double dgp5 = dgpCalculation(len5, MyConst.P8_PHANTOM_DIAGONAL);
		double len6 = segmentCalculation(xPoints[1], yPoints[1], xPoints[3], yPoints[3], dimPixel);
		double dgp6 = dgpCalculation(len6, MyConst.P8_PHANTOM_DIAGONAL);

		double[] vetResults = { len1, len2, len3, len4, len5, len6, dgp1, dgp2, dgp3, dgp4, dgp5, dgp6 };
		return vetResults;
	}

	public static int howmanyPoints(Polygon poli1) {
		int nPunti;
		if (poli1 == null) {
			nPunti = 0;
		} else {
			nPunti = poli1.npoints;
		}
		return nPunti;
	}

	/**
	 * extract the test images from test2.jar in a temporary directory
	 * 
	 * @return home1 path of temporary directory
	 */
	private String findTestImages() {
		InputOutput io = new InputOutput();

		io.extractFromJAR(MyConst.TEST_FILE, "HT2A_testP8", "./Test2/");
		io.extractFromJAR(MyConst.TEST_FILE, "HT5A2_testP8", "./Test2/");
		String home1 = this.getClass().getResource(MyConst.TEST_DIRECTORY).getPath();
		return (home1);
	}

}
