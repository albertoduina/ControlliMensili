package contMensili;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.ImageWindow;
import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.ContrastEnhancer;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.RoiManager;

import java.awt.Color;
import java.util.StringTokenizer;

import utils.AboutBox;
import utils.ButtonMessages;
import utils.CustomCanvasGeneric;
import utils.InputOutput;
import utils.MyMsg;
import utils.MyConst;
import utils.MyLog;
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
 * Analizza la MTF, in base al posizionamento di un segmento vengono inserite
 * nel RoiManager Roi posizionate su tutte le line pairs e su acqua e plexiglas.
 * Su queste Roi vengono eseguiti i calcoli.
 * 
 * Per salvare i dati in formato xls necessita di Excel_Writer.jar nella
 * directory plugins
 * 
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 * 
 */

public class p4rmn_ implements PlugIn, Measurements {

	public static String VERSION = "MTF";

	private static String TYPE = " >> CONTROLLO MTF_____________________";

	// private final String TEST_DIRECTORY = "/test2/";

	// private final String TEST_FILE = "test2.jar";

	public static final int ABORT = 1;

	/**
	 * directory dati, dove vengono memorizzati ayv.txt e Results1.xls
	 */
	public String fileDir = Prefs.get("prefer.string1", "none");

	/**
	 * tabella coi dati di ayv.txt (generati da Sequenze)
	 */
	public String[][] strRiga3;

	/**
	 * tabella coi dati di codici.txt
	 */
	public String[][] tabl;

	/**
	 * true se viene selezionato PASSO
	 */
	public boolean bstep = false;

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

		int nTokens = new StringTokenizer(args, "#").countTokens();
		if (nTokens == 0) {
			manualMenu(0, "");
		} else {
			autoMenu(args);
		}
		return;
	}

	/**
	 * genera una directory temporanea e vi estrae le immagini di test da test2.jar
	 * 
	 * @return home1 path della directory temporanea con le immagini di test
	 */
	private String findTestImages() {

		InputOutput io = new InputOutput();

		io.extractFromJAR(MyConst.TEST_FILE, "HR2A_testP4", "./Test2/");
		io.extractFromJAR(MyConst.TEST_FILE, "HR2A2_testP4", "./Test2/");
		String home1 = this.getClass().getResource(MyConst.TEST_DIRECTORY).getPath();
		return (home1);
	} // findTestImages

	public int manualMenu(int preset, String testDirectory) {
		boolean retry = false;
		// boolean step = false;
		do {
			int userSelection1 = UtilAyv.userSelectionManual(VERSION, TYPE);
			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				retry = false;
				return 0;
			case 2:
				// new AboutBox().about("Controllo MTF", this.getClass());
				new AboutBox().about("Controllo MTF", MyVersion.CURRENT_VERSION);
				retry = true;
				break;
			case 3:
				selfTestMenu();
				retry = false;
				break;
			case 4:
				// step = true;
			case 5:
				boolean verbose = true;
				boolean test = false;
				boolean autoCalled = true;
				boolean step = true;
				String path1 = UtilAyv.imageSelection("SELEZIONARE IMMAGINE...");
				if (path1 == null)
					return 0;

				prepMTF(path1, "", autoCalled, step, verbose, test);
				retry = true;
				UtilAyv.afterWork();
				break;
			}
		} while (retry);
		new AboutBox().close();
		UtilAyv.afterWork();
		return 0;
	}

	/**
	 * Auto menu invoked from Sequenze_
	 * 
	 * @param autoArgs
	 * @return
	 */
	public int autoMenu(String autoArgs) {
		MyLog.appendLog(fileDir + "MyLog.txt", "p4 riceve " + autoArgs);

		// the autoArgs are passed from Sequenze_
		// possibilities:
		// 1 token -1 = silentAutoTest
		// 2 tokens auto
		// 4 tokens auto
		//

		int nTokens = new StringTokenizer(autoArgs, "#").countTokens();
		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);
		if (vetRiga[0] == -1) {
			IJ.log("selfTestSilent.p4rmn_");
			selfTestSilent();
			return 0;
		}

		if (nTokens != MyConst.TOKENS1) {
			MyMsg.msgParamError();
			IJ.log("p4rmn ERRORE PARAMETRI CHIAMATA nTokens =" + nTokens + " invece di 1");
			return 0;
		}

		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir + MyConst.SEQUENZE_FILE);
		String path1 = "";

		path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);

		boolean retry = false;
		boolean step = false;

		do {
			int userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE,
					TableSequence.getCode(iw2ayvTable, vetRiga[0]), TableSequence.getCoil(iw2ayvTable, vetRiga[0]),
					vetRiga[0] + 1, TableSequence.getLength(iw2ayvTable));
			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				return 0;
			case 2:
				// new AboutBox().about("Contollo MTF", this.getClass());
				new AboutBox().about("Controllo MTF", MyVersion.CURRENT_VERSION);
				retry = true;
				break;
			case 3:
				step = true;
			case 4:
				boolean verbose = true;
				boolean test = false;
				boolean autoCalled = true;
				// double[] roiData = readPreferences();
				ResultsTable rt = prepMTF(path1, autoArgs, autoCalled, step, verbose, test);
				UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, rt);
				retry = false;
				break;
			}
		} while (retry);
		new AboutBox().close();
		UtilAyv.afterWork();
		return 0;
	}

	public static ResultsTable prepMTF(String path1, String autoArgs, boolean autoCalled, boolean step, boolean verbose,
			boolean test) {

		double[] vetPreferences = readPreferences();
		ResultsTable rt = mainMTF(path1, vetPreferences, autoCalled, step, verbose, test);
		return rt;
	}

	@SuppressWarnings("deprecation")
	public static ResultsTable mainMTF(String path1, double[] vetPreferences, boolean autoCalled, boolean step,
			boolean verbose, boolean test) {

		boolean accetta = false;
		ResultsTable rt = null;

		UtilAyv.setMeasure(MEAN + STD_DEV);

		do {
			ImagePlus imp1 = null;

			if (verbose)
				imp1 = UtilAyv.openImageMaximized(path1);
			else
				imp1 = UtilAyv.openImageNoDisplay(path1, verbose);

			// l'immagine deve esere visualizzata perchè uso RoiManager
			if (imp1 == null) {
				MyLog.waitHere("Immagine non trovata " + path1);
				return null;
			}
			
			// 291219 iw2ayv
			new ContrastEnhancer().stretchHistogram(imp1.getProcessor(), 0.5);


			double dimPixel = ReadDicom.readDouble(
					ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 1));

			int width = imp1.getWidth();
			int height = imp1.getHeight();
			// double wid = imp1.getWidth();

			double xStartRefline = vetPreferences[0];
			double yStartRefline = vetPreferences[1];
			double xEndRefline = vetPreferences[2];
			double yEndRefline = vetPreferences[3];

			// IJ.log("readPreferences " + xStartRefline + ", " + yStartRefline
			// + ", " + xEndRefline + ", " + yEndRefline);

			if (!test) {
				if (((Math.abs(xStartRefline - xEndRefline) < 5) && (Math.abs(yStartRefline - yEndRefline) < 5))
						|| ((Math.abs(xStartRefline - xEndRefline) > width)
								&& (Math.abs(yStartRefline - yEndRefline) > height))) {
					IJ.log("p4rmn_ INTERVENUTO OVERRIDE");
					xStartRefline = 20.;
					yStartRefline = 25.;
					xEndRefline = 100.;
					yEndRefline = 120.;
				}
			}

			double xStartRefline2 = xStartRefline / dimPixel;
			double yStartRefline2 = yStartRefline / dimPixel;
			double xEndRefline2 = xEndRefline / dimPixel;
			double yEndRefline2 = yEndRefline / dimPixel;

			int xStartReflineScreen;
			int yStartReflineScreen;
			int xEndReflineScreen;
			int yEndReflineScreen;

			xStartReflineScreen = (int) xStartRefline2;
			yStartReflineScreen = (int) yStartRefline2;
			xEndReflineScreen = (int) xEndRefline2;
			yEndReflineScreen = (int) yEndRefline2;
			imp1.setRoi(new Line(xStartReflineScreen, yStartReflineScreen, xEndReflineScreen, yEndReflineScreen));
			imp1.updateAndDraw();

			if (verbose)
				msgLinePositioning();

			//
			// Leggo la posizione finale del segmento
			//
			Roi roi = imp1.getRoi();
			Line l1 = (Line) roi;
			// coordinate dopo posizionamento segmento
			int xStartReflineUser = l1.x1;
			int yStartReflineUser = l1.y1;
			int xEndReflineUser = l1.x2;
			int yEndReflineUser = l1.y2;

			double[] data1 = new double[4];
			data1[0] = xStartReflineUser * dimPixel;
			data1[1] = yStartReflineUser * dimPixel;
			data1[2] = xEndReflineUser * dimPixel;
			data1[3] = yEndReflineUser * dimPixel;

			// IJ.log("savePreferences " + data1[0] + ", " + data1[1] + ", "
			// + data1[2] + ", " + data1[3]);

			writePreferences(data1);
			// -----------------------------------------------------------------------/
			// determino ora la posizione rotrotraslata delle
			// diverse roi per il 1024 risoluzione 2 mmlp
			// -----------------------------------------------------------------------/
			// Il punto in altro a sx del nostro segmento diventa il punto di
			// origine
			//
			int xRoi = 0;
			int yRoi = 0;
			// -------------- 2 mm
			xRoi = (int) (MyConst.P4_X_ROI_POSITION[0] / dimPixel);
			yRoi = (int) (MyConst.P4_Y_ROI_POSITION[0] / dimPixel);
			double[] dsd1 = UtilAyv.coord2D(xStartReflineUser, yStartReflineUser, xEndReflineUser, yEndReflineUser,
					xRoi, yRoi, false);
			// -------------- 1.5 mm
			xRoi = (int) (MyConst.P4_X_ROI_POSITION[1] / dimPixel);
			yRoi = (int) (MyConst.P4_Y_ROI_POSITION[1] / dimPixel);
			double[] dsd2 = UtilAyv.coord2D(xStartReflineUser, yStartReflineUser, xEndReflineUser, yEndReflineUser,
					xRoi, yRoi, false);
			// -------------- 1 mm
			xRoi = (int) (MyConst.P4_X_ROI_POSITION[2] / dimPixel);
			yRoi = (int) (MyConst.P4_Y_ROI_POSITION[2] / dimPixel);
			double[] dsd3 = UtilAyv.coord2D(xStartReflineUser, yStartReflineUser, xEndReflineUser, yEndReflineUser,
					xRoi, yRoi, false);
			// -------------- 0.5 mm
			xRoi = (int) (MyConst.P4_X_ROI_POSITION[3] / dimPixel);
			yRoi = (int) (MyConst.P4_Y_ROI_POSITION[3] / dimPixel);
			double[] dsd4 = UtilAyv.coord2D(xStartReflineUser, yStartReflineUser, xEndReflineUser, yEndReflineUser,
					xRoi, yRoi, false);
			// -------------- 0.3 mm
			xRoi = (int) (MyConst.P4_X_ROI_POSITION[4] / dimPixel);
			yRoi = (int) (MyConst.P4_Y_ROI_POSITION[4] / dimPixel);
			double[] dsd5 = UtilAyv.coord2D(xStartReflineUser, yStartReflineUser, xEndReflineUser, yEndReflineUser,
					xRoi, yRoi, false);
			// -------------- plexiglas
			xRoi = (int) (MyConst.P4_X_ROI_POSITION[5] / dimPixel);
			yRoi = (int) (MyConst.P4_Y_ROI_POSITION[5] / dimPixel);
			double[] dsd6 = UtilAyv.coord2D(xStartReflineUser, yStartReflineUser, xEndReflineUser, yEndReflineUser,
					xRoi, yRoi, false);
			// -------------- acqua
			xRoi = (int) (MyConst.P4_X_ROI_POSITION[6] / dimPixel);
			yRoi = (int) (MyConst.P4_Y_ROI_POSITION[6] / dimPixel);
			double[] dsd7 = UtilAyv.coord2D(xStartReflineUser, yStartReflineUser, xEndReflineUser, yEndReflineUser,
					xRoi, yRoi, false);

			// -------------------------------------------------------/

			// offset rispetto al centro dell' immagine (nel nostro caso
			// sarà rispetto al centro del fantoccio) espressi in pixel

			int dRoi2mm = (int) (MyConst.P4_DIA_ROI[0] / dimPixel);
			int xRoi2mm = (int) (dsd1[0] - dRoi2mm / 2);
			int yRoi2mm = (int) (dsd1[1] - dRoi2mm / 2);

			int dRoi1_5mm = (int) (MyConst.P4_DIA_ROI[1] / dimPixel);
			int xRoi1_5mm = (int) (dsd2[0] - dRoi1_5mm / 2);
			int yRoi1_5mm = (int) (dsd2[1] - dRoi1_5mm / 2);

			int dRoi1mm = (int) (MyConst.P4_DIA_ROI[2] / dimPixel);
			int xRoi1mm = (int) (dsd3[0] - dRoi1mm / 2);
			int yRoi1mm = (int) (dsd3[1] - dRoi1mm / 2);

			int dRoi_5mm = (int) (MyConst.P4_DIA_ROI[3] / dimPixel);
			int xRoi_5mm = (int) (dsd4[0] - dRoi_5mm / 2);
			int yRoi_5mm = (int) (dsd4[1] - dRoi_5mm / 2);

			int dRoi_3mm = (int) (MyConst.P4_DIA_ROI[4] / dimPixel);
			int xRoi_3mm = (int) (dsd5[0] - dRoi_3mm / 2);
			int yRoi_3mm = (int) (dsd5[1] - dRoi_3mm / 2);

			int dRoi_acqua = (int) (MyConst.P4_DIA_ROI[5] / dimPixel);
			int xRoi_acqua = (int) (dsd6[0] - dRoi_acqua / 2);
			int yRoi_acqua = (int) (dsd6[1] - dRoi_acqua / 2);

			int dRoi_plexi = (int) (MyConst.P4_DIA_ROI[6] / dimPixel);
			int xRoi_plexi = (int) (dsd7[0] - dRoi_plexi / 2);
			int yRoi_plexi = (int) (dsd7[1] - dRoi_plexi / 2);

			// provo a vedere se sono in grado di sostituirmi alla macro del ROI
			// MANAGER

			// Overlay over1 = new Overlay();
			// imp1.setOverlay(over1);

			RoiManager rm1 = RoiManager.getInstance();
			if (rm1 == null)
				rm1 = new RoiManager();
			imp1.setRoi(new OvalRoi(xRoi2mm, yRoi2mm, dRoi2mm, dRoi2mm));
			rm1.addRoi(imp1.getRoi());
			// over1.addElement(imp1.getRoi());
			imp1.setRoi(new OvalRoi(xRoi1_5mm, yRoi1_5mm, dRoi1_5mm, dRoi1_5mm));
			rm1.addRoi(imp1.getRoi());
			// over1.addElement(imp1.getRoi());
			imp1.setRoi(new OvalRoi(xRoi1mm, yRoi1mm, dRoi1mm, dRoi1mm));
			rm1.addRoi(imp1.getRoi());
			// over1.addElement(imp1.getRoi());
			imp1.setRoi(new OvalRoi(xRoi_5mm, yRoi_5mm, dRoi_5mm, dRoi_5mm));
			rm1.add(imp1, imp1.getRoi(), 4);
			// over1.addElement(imp1.getRoi());
			imp1.setRoi(new OvalRoi(xRoi_3mm, yRoi_3mm, dRoi_3mm, dRoi_3mm));
			rm1.addRoi(imp1.getRoi());
			// over1.addElement(imp1.getRoi());
			imp1.setRoi(new OvalRoi(xRoi_acqua, yRoi_acqua, dRoi_acqua, dRoi_acqua));
			rm1.addRoi(imp1.getRoi());
			// over1.addElement(imp1.getRoi());
			imp1.setRoi(new OvalRoi(xRoi_plexi, yRoi_plexi, dRoi_plexi, dRoi_plexi));
			rm1.addRoi(imp1.getRoi());

			// over1.addElement(imp1.getRoi());

			if (verbose)
				rm1.runCommand(imp1, "Combine");

			if (verbose)
				msgRefinePositioning();

			rm1.runCommand(imp1, "Measure");

			ResultsTable rt1 = ResultsTable.getResultsTable();

			rm1.runCommand("Delete");

			int visualResolution = 0;
			// qui applichiamo il custom canvas generico
			if (verbose && !test) {

				overlayNumbers(imp1, true);

				visualResolution = msgLastVisiblePositioning();
			}

			//
			// Display dei risultati
			//
			rm1.close();

			if (rt1 == null) {
				IJ.log("rt1 == null");
				return null;
			}

			int nDati = rt1.getCounter();
			int[][] tabValori = new int[nDati][2];
			for (int i2 = 0; i2 < nDati; i2++) {
				tabValori[i2][0] = (int) rt1.getValue("Mean", i2);
				tabValori[i2][1] = (int) rt1.getValue("StdDev", i2);
			}

			UtilAyv.closeResultsWindow();

			double[] vetCalc = calculationMTF(tabValori);

			double mod20 = vetCalc[0];
			double mod15 = vetCalc[1];
			double mod10 = vetCalc[2];
			double mod05 = vetCalc[3];
			double mod03 = vetCalc[4];
			// String[][] tabCodici = TableCode
			// .loadMultipleTable(MyConst.CODE_GROUP);
			TableCode tc1 = new TableCode();
			String[][] tabCodici = tc1.loadMultipleTable("codici", ".csv");

			ImagePlus imp2 = UtilAyv.openImageNoDisplay(path1, verbose);

			// must open another time the image because a strange problem in
			// junit test of SelfTestSilent

			String[] info1 = ReportStandardInfo.getSimpleStandardInfo(path1, imp2, tabCodici, VERSION, autoCalled);

			// put values in ResultsTable
			rt = ReportStandardInfo.putSimpleStandardInfoRT(info1);
			rt.showRowNumbers(true);

			String t1 = "TESTO";
			String s2 = "VALORE";
			String s3 = "roi_x";
			String s4 = "roi_y";
			String s5 = "roi_b";
			String s6 = "roi_h";

			// rt.setHeading(++col, "roi_x");
			// rt.setHeading(++col, "roi_y");
			// rt.setHeading(++col, "roi_b");
			// rt.setHeading(++col, "roi_h");
			rt.addLabel(t1, "segm_riferim");
			rt.addValue(s2, 0);
			rt.addValue(s3, xStartRefline);
			rt.addValue(s4, yStartRefline);
			rt.addValue(s5, xEndRefline);
			rt.addValue(s6, yEndRefline);

			rt.incrementCounter();

			rt.addLabel(t1, "MTF_2.0_LPmm");
			rt.addValue(s2, mod20);
			rt.addValue(s3, xRoi2mm);
			rt.addValue(s4, yRoi2mm);
			rt.addValue(s5, dRoi2mm);
			rt.addValue(s6, dRoi2mm);

			rt.incrementCounter();
			rt.addLabel(t1, "MTF_1.5_LPmm");
			rt.addValue(s2, mod15);
			rt.addValue(s3, xRoi1_5mm);
			rt.addValue(s4, yRoi1_5mm);
			rt.addValue(s5, dRoi1_5mm);
			rt.addValue(s6, dRoi1_5mm);

			rt.incrementCounter();
			rt.addLabel(t1, "MTF_1.0_LPmm");
			rt.addValue(s2, mod10);
			rt.addValue(s3, xRoi1mm);
			rt.addValue(s4, yRoi1mm);
			rt.addValue(s5, dRoi1mm);
			rt.addValue(s6, dRoi1mm);

			rt.incrementCounter();
			rt.addLabel(t1, "MTF_0.5_LPmm");
			rt.addValue(s2, mod05);
			rt.addValue(s3, xRoi_5mm);
			rt.addValue(s4, yRoi_5mm);
			rt.addValue(s5, dRoi_5mm);
			rt.addValue(s6, dRoi_5mm);

			rt.incrementCounter();
			rt.addLabel(t1, "MTF_0.3_LPmm");
			rt.addValue(s2, mod03);
			rt.addValue(s3, xRoi_3mm);
			rt.addValue(s4, yRoi_3mm);
			rt.addValue(s5, dRoi_3mm);
			rt.addValue(s6, dRoi_3mm);

			rt.incrementCounter();
			rt.addLabel(t1, "DimPix");
			rt.addValue(s2, dimPixel);

			rt.incrementCounter();
			rt.addLabel(t1, "Visual");
			rt.addValue(s2, visualResolution);

			if (verbose && !test)
				rt.show("Results");

			if (autoCalled && !test) {
				accetta = MyMsg.accettaMenu();
			} else {
				if (!test) {
					accetta = MyMsg.msgStandalone();
				} else
					imp1.close();
				accetta = test;
			}
		} while (!accetta);

		return rt;
	}

	public static double[] readPreferences() {

		double xStartRefline = ReadDicom.readDouble(Prefs.get("prefer.p4rmnAx", "" + 0.4));
		double yStartRefline = ReadDicom.readDouble(Prefs.get("prefer.p4rmnAy", "" + 0.4));
		double xEndRefline = ReadDicom.readDouble(Prefs.get("prefer.p4rmnBx", "" + 0.8));
		double yEndRefline = ReadDicom.readDouble(Prefs.get("prefer.p4rmnBy", "" + 0.8));
		double[] defaults = { xStartRefline, yStartRefline, xEndRefline, yEndRefline };
		return defaults;
	}

	public static void writePreferences(double[] selection) {

		Prefs.set("prefer.p4rmnAx", "" + selection[0]);
		Prefs.set("prefer.p4rmnAy", "" + selection[1]);
		Prefs.set("prefer.p4rmnBx", "" + selection[2]);
		Prefs.set("prefer.p4rmnBy", "" + selection[3]);
	}

	private static void msgLinePositioning() {
		ButtonMessages.ModelessMsg(
				"Far coincidere il segmento  con il lato esterno sx delle linee 2 mm e premere CONTINUA", "CONTINUA");
	}

	private static void msgRefinePositioning() {

		ButtonMessages.ModelessMsg("Posizionare con maggiore esattezza le roi e poi premere CONTINUA", "CONTINUA");
	}

	private static int msgLastVisiblePositioning() {

		int userSelection1 = ButtonMessages.ModelessMsg("Quale è l'ultimo gruppo visibile?", "- 5 -", "- 4 -", "- 3 -",
				"- 2 -", "- 1 -");
		return (userSelection1);
	}

	public static void overlayNumbers(ImagePlus imp1, boolean verbose) {
		imp1.setRoi(new OvalRoi(0, 0, 0, 0));
		double dimPixel = ReadDicom.readDouble(
				ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 1));

		CustomCanvasGeneric ccg1 = new CustomCanvasGeneric(imp1);

		double[] cx1 = { 53, 80, 70, 58, 168 };
		double[] cy1 = { 99, 165, 85, 130, 67 };

		for (int i1 = 0; i1 < cx1.length; i1++) {
			cx1[i1] = cx1[i1] / dimPixel;
			cy1[i1] = cy1[i1] / dimPixel;
		}

		ccg1.setPosition1(cx1, cy1);
		ccg1.setColor1(Color.red);

		if (verbose) {
			ImageWindow iw1 = new ImageWindow(imp1, ccg1);
			iw1.maximize();
		}
	}

	/**
	 * Siemens test image expected results
	 * 
	 * @return
	 */
	double[] referenceSiemens() {
		double mod20 = 0.9598900737778213;
		double mod15 = 0.9866962668164938;
		double mod10 = 0.9883716512349785;
		double mod05 = 0.14203500307561293;
		double mod03 = 0.3751277435706519;
		double[] vetReference = { mod20, mod15, mod10, mod05, mod03 };
		return vetReference;
	}

	/**
	 * Ge test image expected results
	 * 
	 * @return
	 */
	double[] referenceGe() {
		double mod20 = 0.9648520059889102;
		double mod15 = 1.0191761573797278;
		double mod10 = 0.8318990365553754;
		double mod05 = 0.14254549598153793;
		double mod03 = 0.2685309238398184;
		double[] vetReference = { mod20, mod15, mod10, mod05, mod03 };
		return vetReference;
	}

	/**
	 * Self test execution menu
	 */
	public void selfTestMenu() {
		if (new InputOutput().checkJar(MyConst.TEST_FILE)) {
			int userSelection2 = UtilAyv.siemensGe();
			switch (userSelection2) {

			case 1: {
				// GE
				String home1 = findTestImages();
				String path1 = home1 + "/HR2A2_testP4";
				boolean autoCalled = false;
				boolean step = false;
				boolean verbose = true;
				boolean test = true;
				double[] vetReference = referenceGe();
				double[] vetLinePositions = { MyConst.P4_X_START_REFLINE_TESTGE, MyConst.P4_Y_START_REFLINE_TESTGE,
						MyConst.P4_X_END_REFLINE_TESTGE, MyConst.P4_Y_END_REFLINE_TESTGE };
				ResultsTable rt1 = mainMTF(path1, vetLinePositions, autoCalled, step, verbose, test);
				double[] vetOutput = UtilAyv.vectorizeResults(rt1);

				double[] vetResults = new double[5];
				vetResults[0] = vetOutput[1];
				vetResults[1] = vetOutput[2];
				vetResults[2] = vetOutput[3];
				vetResults[3] = vetOutput[4];
				vetResults[4] = vetOutput[5];

				boolean ok = UtilAyv.verifyResults1(vetResults, vetReference, MyConst.P4_vetName);
				if (ok)
					MyMsg.msgTestPassed();
				else
					MyMsg.msgTestFault();
				UtilAyv.afterWork();
				break;
			}
			case 2:
				// Siemens
				String home1 = findTestImages();
				String path1 = home1 + "/HR2A_testP4";
				double[] vetReference = referenceSiemens();
				boolean autoCalled = false;
				boolean step = false;
				boolean verbose = true;
				boolean test = true;
				double[] vetLinePositions = { MyConst.P4_X_START_REFLINE_TESTSIEMENS,
						MyConst.P4_Y_START_REFLINE_TESTSIEMENS, MyConst.P4_X_END_REFLINE_TESTSIEMENS,
						MyConst.P4_Y_END_REFLINE_TESTSIEMENS };
				ResultsTable rt1 = mainMTF(path1, vetLinePositions, autoCalled, step, verbose, test);
				double[] vetOutput = UtilAyv.vectorizeResults(rt1);

				double[] vetResults = new double[5];
				vetResults[0] = vetOutput[1];
				vetResults[1] = vetOutput[2];
				vetResults[2] = vetOutput[3];
				vetResults[3] = vetOutput[4];
				vetResults[4] = vetOutput[5];

				boolean ok = UtilAyv.verifyResults1(vetResults, vetReference, MyConst.P4_vetName);
				if (ok)
					MyMsg.msgTestPassed();
				else
					MyMsg.msgTestFault();
				UtilAyv.afterWork();
				break;
			}
		} else {
			UtilAyv.noTest2();
		}
		return;
	}

	/**
	 * Automatic silent self test
	 */
	public void selfTestSilent() {
		// if (new InputOutput().checkJar(MyConst.TEST_FILE)) {
		String home1 = findTestImages();
		String path1 = home1 + "/HR2A_testP4";
		double[] vetReference = referenceSiemens();

		String autoArgs = "-1";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = false;
		boolean test = true;

		// double xStartRefline = 52.0;
		// double yStartRefline = 112.0;
		// double xEndRefline = 140.0;
		// double yEndRefline = 207.0;

		double xStartRefline = MyConst.P4_X_START_REFLINE_TESTSIEMENS;
		double yStartRefline = MyConst.P4_Y_START_REFLINE_TESTSIEMENS;
		double xEndRefline = MyConst.P4_X_END_REFLINE_TESTSIEMENS;
		double yEndRefline = MyConst.P4_Y_END_REFLINE_TESTSIEMENS;

		double[] vetPreferences = { xStartRefline, yStartRefline, xEndRefline, yEndRefline };

		ResultsTable rt1 = mainMTF(path1, vetPreferences, autoCalled, step, verbose, test);

		// UtilAyv.dumpResultsTable(rt1);
		double[] vetOutput = UtilAyv.vectorizeResults(rt1);

		double[] vetResults = new double[5];
		vetResults[0] = vetOutput[1];
		vetResults[1] = vetOutput[2];
		vetResults[2] = vetOutput[3];
		vetResults[3] = vetOutput[4];
		vetResults[4] = vetOutput[5];

		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference, MyConst.P4_vetName);

		// UtilAyv.closeResultsWindow();
		if (ok) {
			IJ.log("Il test di p4rmn_ MTF è stato SUPERATO");
		} else {
			IJ.log("Il test di p4rmn_ MTF evidenzia degli ERRORI");
		}
		return;
	}

	public static double[] calculationMTF(int[][] tabValori) {
		double ds20 = tabValori[0][1];
		double ds15 = tabValori[1][1];
		double ds10 = tabValori[2][1];
		double ds05 = tabValori[3][1];
		double ds03 = tabValori[4][1];

		double dsAcqua = tabValori[5][1];
		double dsPlexi = tabValori[6][1];
		double siAcqua = tabValori[5][0];
		double siPlexi = tabValori[6][0];

		double mod20 = (Math.PI * Math.sqrt(2.0) / 2.0)
				* (Math.sqrt(Math.pow(ds20, 2) - (Math.pow(dsAcqua, 2) + Math.pow(dsPlexi, 2)) / 2)
						/ Math.abs(siPlexi - siAcqua));
		double mod15 = (Math.PI * Math.sqrt(2.0) / 2.0)
				* (Math.sqrt(Math.pow(ds15, 2) - (Math.pow(dsAcqua, 2) + Math.pow(dsPlexi, 2)) / 2)
						/ Math.abs(siPlexi - siAcqua));
		double mod10 = (Math.PI * Math.sqrt(2.0) / 2.0)
				* (Math.sqrt(Math.pow(ds10, 2) - (Math.pow(dsAcqua, 2) + Math.pow(dsPlexi, 2)) / 2)
						/ Math.abs(siPlexi - siAcqua));
		double mod05 = (Math.PI * Math.sqrt(2.0) / 2.0)
				* (Math.sqrt(Math.pow(ds05, 2) - (Math.pow(dsAcqua, 2) + Math.pow(dsPlexi, 2)) / 2)
						/ Math.abs(siPlexi - siAcqua));
		double mod03 = (Math.PI * Math.sqrt(2.0) / 2.0)
				* (Math.sqrt(Math.pow(ds03, 2) - (Math.pow(dsAcqua, 2) + Math.pow(dsPlexi, 2)) / 2)
						/ Math.abs(siPlexi - siAcqua));
		double[] vetCalc = new double[5];

		vetCalc[0] = mod20;
		vetCalc[1] = mod15;
		vetCalc[2] = mod10;
		vetCalc[3] = mod05;
		vetCalc[4] = mod03;

		return vetCalc;
	}

} // p4rmn
