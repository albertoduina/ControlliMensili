package contMensili;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.ImageWindow;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.StringTokenizer;

import utils.AboutBox;
import utils.ButtonMessages;
import utils.MyLog;
import utils.MyMsg;
import utils.MyConst;
import utils.CustomCanvasGeneric;
import utils.InputOutput;
import utils.MyVersionUtils;
import utils.ReadDicom;
import utils.ReportStandardInfo;
import utils.TableCode;
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
 * Analizza il WARP , le rods vengono selezionate manualmente. Dalla v4.00
 * risultati in pixels
 * 
 * Per salvare i dati in formato xls necessita di Excel_Writer.jar nella
 * directory plugins
 * 
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 * 
 */

public class p7rmn_ implements PlugIn, Measurements {

	static final int ABORT = 1;

	public static String VERSION = "WARP";

	private static String TYPE = " >> CONTROLLO WARP____________________";

	// ---------------------------"01234567890123456789012345678901234567890"

	/**
	 * directory dati, dove vengono memorizzati ayv.txt e Results1.xls
	 */
	private static String fileDir = "";

	/**
	 * tabella coi dati di ayv.txt (generati da Sequenze)
	 */
	String[][] iw2ayvTable;

	/**
	 * immagine da analizzare
	 */
	ImagePlus imp1;

	double dimPixel2;

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

		VERSION = className + "_build_" + MyVersion.getVersion() + "_iw2ayv_build_" + MyVersionUtils.getVersion();

		fileDir = Prefs.get("prefer.string1", "none");

		int nTokens = new StringTokenizer(args, "#").countTokens();
		if (nTokens == 0) {
			manualMenu(0, "");
		} else {
			autoMenu(args);
		}
	}

	public int manualMenu(int preset, String testDirectory) {
		boolean retry = false;
		boolean step = false;
		int riga1 = 0;
		do {

			int userSelection1 = UtilAyv.userSelectionManual(VERSION, TYPE);
			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				retry = false;
				return 0;
			case 2:
				// new AboutBox().about("Controllo Warp", this.getClass());
				new AboutBox().about("Controllo Warp", MyVersion.CURRENT_VERSION);
				retry = true;
				break;
			case 3:
				selfTestAyv();
				retry = true;
				break;
			case 4:
				step = true;
			case 5:
				String path1 = UtilAyv.imageSelection("SELEZIONARE IMMAGINE...");
				if (path1 == null)
					return 5;
				boolean autoCalled = false;
				boolean verbose = true;
				boolean test = false;
				mainWarp(path1, riga1, autoCalled, step, verbose, test);
				UtilAyv.afterWork();
				retry = false;
				return 5;
			}
		} while (retry);
		UtilAyv.afterWork();
		new AboutBox().close();
		return 0;
	}

	public int autoMenu(String autoArgs) {
		MyLog.appendLog(fileDir + "MyLog.txt", "p7 riceve " + autoArgs);

		StringTokenizer st = new StringTokenizer(autoArgs, "#");
		int nTokens = st.countTokens();
		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);
		if (vetRiga[0] == -1) {
			IJ.log("selfTestSilent.p7rmn_");
			selfTestSilent();
			return 0;
		}
		if (nTokens > 1) {
			MyMsg.msgParamError();
			return 0;
		}
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
				// new AboutBox().about("Controllo Warp", this.getClass());
				new AboutBox().about("Controllo Warp", MyVersion.CURRENT_VERSION);
				retry = true;
				break;
			case 3:
				step = true;
			case 4:
				boolean verbose = true;
				boolean test = false;
				boolean autoCalled = true;
				iw2ayvTable = new TableSequence().loadTable(fileDir + MyConst.SEQUENZE_FILE);
				String path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
				ResultsTable rt = mainWarp(path1, vetRiga[0], autoCalled, step, verbose, test);
				UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, rt);

				UtilAyv.afterWork();
				retry = false;
				break;
			}
		} while (retry);
		UtilAyv.afterWork();
		new AboutBox().close();
		return 0;
	}

	@SuppressWarnings("deprecation")
	public ResultsTable mainWarp(String path1, int riga1, boolean autoCalled, boolean step, boolean verbose,
			boolean test) {
		boolean accetta = false;
		UtilAyv.setMeasure(MEAN + STD_DEV);
		ResultsTable rt = null;
		// --------------------------------------------------------------------------------------/
		// Qui si torna se la misura è da rifare
		// --------------------------------------------------------------------------------------/
		do {
			imp1 = UtilAyv.openImageMaximized(path1);
			IJ.run(imp1, "Set Scale...", "distance=0 known=0 pixel=1 unit=pixel");

			IJ.run("Enhance Contrast", "saturated=10 normalize ");
			dimPixel2 = ReadDicom.readDouble(
					ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 1));
			String slicePos = ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_IMAGE_POSITION),
					3);
			double diamRoi1 = (double) MyConst.P7_DIAM_ROI / dimPixel2;
			int diamRoi = (int) diamRoi1;
			boolean circular = true;
			UtilAyv.presetRoi(imp1, diamRoi, circular);
			msgPositionRoi();
			overlayRodNumbers(imp1, diamRoi, true);
			Polygon poli1 = UtilAyv.selectionPointsClick(imp1,
					"Cliccare nell'ordine su tutte le RODS, poi premere FINE POSIZIONAMENTO", "FINE POSIZIONAMENTO");
			int nPunti;
			if (poli1 == null) {
				nPunti = 0;
			} else {
				nPunti = poli1.npoints;
			}

			if (nPunti == MyConst.P7_TOTAL_NUM_POINTS) {
				int[] xPoints = poli1.xpoints;
				int[] yPoints = poli1.ypoints;
				int[][] tabPunti = new int[nPunti][2];
				for (int i1 = 0; i1 < nPunti; i1++) {
					tabPunti[i1][0] = xPoints[i1];
					tabPunti[i1][1] = yPoints[i1];
				}
				// String[][] tabCodici = new InputOutput().readFile1(
				// MyConst.CODE_FILE, MyConst.TOKENS4);

				// String[][] tabCodici = TableCode
				// .loadMultipleTable(MyConst.CODE_GROUP);

				TableCode tc1 = new TableCode();
				String[][] tabCodici = tc1.loadMultipleTable("codici", ".csv");
				String[] info1 = ReportStandardInfo.getSimpleStandardInfo(path1, imp1, tabCodici, VERSION, autoCalled);
				rt = ReportStandardInfo.putSimpleStandardInfoRT(info1);
				String t1 = "TESTO";
				String s2 = "coord_x";
				String s3 = "coord_y";
				String s4 = "dummy1";
				String s5 = "dummy2";
				String s6 = "dummy3";
				String s7 = "dummy4";

				rt.addLabel(t1, "ShiftCentrat");
				rt.addValue(s2, UtilAyv.convertToDouble(slicePos));
				String aux1 = "";
				int aux2 = 0;
				for (int i2 = 0; i2 < MyConst.P7_NUM_RODS; i2 = i2 + 2) {
					aux2++;
					aux1 = "Rod" + aux2 + "a";
					rt.incrementCounter();
					rt.addLabel(t1, aux1);
					rt.addValue(s2, tabPunti[i2][0]);
					rt.addValue(s3, tabPunti[i2][1]);
					rt.addValue(s4, 0);
					rt.addValue(s5, 0);
					rt.addValue(s6, 0);
					rt.addValue(s7, 0);
					rt.incrementCounter();
					aux1 = "Rod" + aux2 + "b";
					rt.addLabel(t1, aux1);
					rt.addValue(s2, tabPunti[i2 + 1][0]);
					rt.addValue(s3, tabPunti[i2 + 1][1]);
					rt.addValue(s4, 0);
					rt.addValue(s5, 0);
					rt.addValue(s6, 0);
					rt.addValue(s7, 0);
				}
				aux2 = 0;
				for (int i2 = MyConst.P7_NUM_RODS; i2 < MyConst.P7_TOTAL_NUM_POINTS; i2++) {
					aux2++;
					aux1 = "Cubo" + aux2;
					rt.incrementCounter();
					rt.addLabel(t1, aux1);
					rt.addValue(s2, tabPunti[i2][0]);
					rt.addValue(s3, tabPunti[i2][1]);
				}
				rt.incrementCounter();
				rt.addLabel(t1, "Spacing");
				rt.addValue(s2, dimPixel2);
				if (verbose && !test)
					rt.show("Results");
			} else {
				msgRedo(nPunti);
			}
			if (autoCalled && !test) {
				accetta = MyMsg.accettaMenu();

			} else {
				if (!test) {
					accetta = MyMsg.msgStandalone();
				} else
					accetta = test;
			}
		} while (!accetta);
		return rt;
	}

	public void overlayRodNumbers(ImagePlus imp1, int diamRoi, boolean verbose) {

		Roi roi2 = imp1.getRoi();
		Rectangle rec2 = roi2.getBounds();
		imp1.setRoi(new OvalRoi(0, 0, 0, 0));
		double bx = rec2.getX();
		double by = rec2.getY();
		double bw = rec2.getWidth() / imp1.getWidth();
		CustomCanvasGeneric ccg1 = new CustomCanvasGeneric(imp1);
		double[] cx1 = { 245, 105, 245, 380, 175, 310, 75, 160, 335, 425, 175, 310, 105, 245, 380, 245 };
		double[] cy1 = { 40, 95, 130, 95, 160, 160, 230, 230, 230, 230, 305, 305, 370, 330, 370, 430 };
		double[] cx2 = { 250, 270, 250, 230 };
		double[] cy2 = { 240, 260, 280, 260 };
		ccg1.setOffset(bx, by, bw);
		ccg1.setPosition1(cx1, cy1);
		ccg1.setColor1(Color.red);
		ccg1.setPosition2(cx2, cy2);
		ccg1.setColor2(Color.green);
		if (verbose) {
			ImageWindow iw1 = new ImageWindow(imp1, ccg1);
			iw1.maximize();
		}
	}

	void selfTestAyv() {
		if (new InputOutput().checkJar(MyConst.TEST_FILE)) {
			int userSelection2 = UtilAyv.siemensGe();
			switch (userSelection2) {
			case 1: {
				// GE
				String home1 = findTestImages();
				String path1 = home1 + "/HWSA2_testP7";
				int[] vetX = MyConst.P7_X_POINTS_TESTGE;
				int[] vetY = MyConst.P7_Y_POINTS_TESTGE;
				boolean ok = testExcecution(path1, vetX, vetY, -10, -17, true);
				if (ok)
					MyMsg.msgTestPassed();
				else
					MyMsg.msgTestFault();
				UtilAyv.afterWork();
				return;
			}
			case 2:
				// Siemens
				String home1 = findTestImages();
				String path1 = home1 + "/HWSA_testP7";
				int[] vetX = MyConst.P7_X_POINTS_TESTSIEMENS;
				int[] vetY = MyConst.P7_Y_POINTS_TESTSIEMENS;
				boolean ok = testExcecution(path1, vetX, vetY, 0, 20, true);
				if (ok)
					MyMsg.msgTestPassed();
				else
					MyMsg.msgTestFault();
				UtilAyv.afterWork();
				return;
			}
		}
		UtilAyv.noTest2();
	}

	void selfTestSilent() {
		String[] list = { "HWSA2_testP7" };
		String[] path2 = new InputOutput().findListTestImages2(MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
		String path1 = path2[0];

		int[] vetX = MyConst.P7_X_POINTS_TESTSIEMENS;
		int[] vetY = MyConst.P7_Y_POINTS_TESTSIEMENS;
		boolean ok = testExcecution(path1, vetX, vetY, 0, 0, false);
		if (ok) {
			IJ.log("Il test di p7rmn_ WARP è stato SUPERATO");
		} else {
			IJ.log("Il test di p7rmn_ WARP evidenzia degli ERRORI");
		}
		return;
	}

	boolean testExcecution(String path1, int[] vetX, int[] vetY, int offX, int offY, boolean verbose) {
		ImagePlus imp1 = null;
		if (verbose) {
			imp1 = UtilAyv.openImageMaximized(path1);
		} else {
			imp1 = UtilAyv.openImageNoDisplay(path1, false);
		}
		double dimPixel2 = ReadDicom.readDouble(
				ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 1));
		double diamRoi1 = (double) MyConst.P7_DIAM_ROI / dimPixel2;
		int diamRoi = (int) diamRoi1;
		boolean circular = true;
		UtilAyv.presetRoi(imp1, diamRoi, offX, offY, circular);
		if (verbose)
			IJ.wait(500);
		overlayRodNumbers(imp1, diamRoi, verbose);
		Polygon poli1 = UtilAyv.clickSimulation(imp1, vetX, vetY);
		boolean ok = UtilAyv.verifyResults2(poli1.xpoints, poli1.ypoints, vetX, vetY, "della ROD ");
		return ok;
	}

	/**
	 * genera una directory temporanea e vi estrae le immagini di test da test2.jar
	 * 
	 * @return home1 path della directory temporanea con le immagini di test
	 */
	private String findTestImages() {
		InputOutput io = new InputOutput();
		io.extractFromJAR(MyConst.TEST_FILE, "HWSA_testP7", "./Test2/");
		io.extractFromJAR(MyConst.TEST_FILE, "HWSA2_testP7", "./Test2/");
		String home1 = this.getClass().getResource(MyConst.TEST_DIRECTORY).getPath();
		return (home1);
	}

	private static void msgPositionRoi() {
		ButtonMessages.ModelessMsg("Posizionare la ROI  e premere CONTINUA", "CONTINUA");
	}

	private static void msgRedo(int nPunti) {
		IJ.showMessage("--- A T T E N Z I O N E ---",
				"Sono stati selezionati solo " + nPunti + " anzichè 36  punti,\n--- R I F A R E ---");
	}

}
