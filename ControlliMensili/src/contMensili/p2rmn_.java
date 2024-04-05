package contMensili;

// set debug 290607

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.NewImage;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.io.Opener;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.StringTokenizer;

import utils.ButtonMessages;
import utils.ImageUtils;
import utils.InputOutput;
import utils.MyConst;
import utils.MyLog;
import utils.MyVersionUtils;
import utils.ReadDicom;
import utils.ReportStandardInfo;
import utils.SimplexBasedRegressor;
import utils.TableCode;
import utils.TableExpand;
import utils.TableSequence;
import utils.UtilAyv;
import utils.AboutBox;

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
 * Calcola T1 e T2 utilizzando l'algoritmo simplex di Nelder & Mead.
 * 
 * Da MRI Analysis Calculator e da MRI Analysis Pack di Karl Schmidt i cui
 * sorgenti sono raggiungibili da http://www.quickvol.com/launch.html e su
 * sourceforge.net
 * 
 * Per salvare i dati in formato xls necessita di Excel_Writer.jar nella
 * directory plugins
 * 
 * Le formule di minimizzazione sono nella classe SimplexBasedRegressor
 * 
 * 29jan07 v 3.00 i risultati sono praticamente identici a MriCalc
 * 
 * @author Karl Schmidt - karl.schmidt@umassmed.edu
 * 
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 * 
 * 
 */
public class p2rmn_ implements PlugIn, Measurements {

	public static final int ABORT = 1;

	public static String VERSION = "Verifica T1 e T2";

	private static String TYPE = " >> CONTROLLO T1 / T2_________________";

	// ---------------------------"01234567890123456789012345678901234567890"

	/**
	 * tabella coi dati di ayv.txt (generati da Sequenze)
	 */
	String[][] strRiga3; // tabella coi dati di ayv.txt (generati da Sequenze)

	/**
	 * tabella coi dati di codici.txt
	 */
	String[][] tabl;

	/**
	 * calibrazioni spaziali e di densita' dell'immagine
	 */
	Calibration cal8;

	/**
	 * true se l'immagine e' una T2
	 */
	boolean typeT2 = false;

	/**
	 * true se viene selezionato PASSO
	 */
	boolean bstep = false;

	/**
	 * mappa contenente i flag dei pixel su cui eseguire la minimizzazione (sono
	 * quelli delle 12 Roi)
	 */
	int[] bMap;

	private final int T2_TEST_IMAGES = 16;

	private final int T1_TEST_IMAGES = 4;

	private final int MAX_IMAGES = 24;

	private final int ARRAY_DIMENSIONS = 4;

	private final String TEST_DIRECTORY = "/test2/";

	private final String TEST_FILE = "test2.jar";

	private final int N_GELS = 12;

	private final int ROI_DIAM = 20;

	private final int SINGLE_DIGIT = 9;

	private final int TOKENS4 = 4;

	private final int MEAN = 2;

	private final int STD_DEV = 4;

	private final int DIAM_ROI_FONDO = 20;

	private final double DIM_PIXEL_FOV_220 = 0.859375;

	// ULC = UpperLeftCorner
	private final String X_ULC_ROI_TESTSIEMENS = "97;148;42;94;144;196;39;91;142;193;88;139";

	private final String Y_ULC_ROI_TESTSIEMENS = "39;41;87;91;92;96;139;143;145;147;194;197";

	private final String X_ULC_ROI_TESTGE = "93;143;39;90;141;193;38;88;141;191;88;139";

	private final String Y_ULC_ROI_TESTGE = "24;25;73;74;76;77;124;125;127;128;176;178";

	private final String SEQUENZE_FILE = "iw2ayv.txt";

	private final String XLS_FILE = "Result1.xls";

	private final String CODE_FILE = "/codici.txt";

	private final String DICOM_ROWS = "0028,0010";

	private final String DICOM_COLUMNS = "0028,0011";

	private final String DICOM_INVERSION_TIME = "0018,0082";

	private final String DICOM_ECHO_TIME = "0018,0081";

	private final String DICOM_PIXEL_SPACING = "0028,0030";

	public boolean debug1 = false;

	public boolean DEBUG2 = false; // true attiva il debug

	private boolean selftest = false; // non e' bello, lo uso per colpa del

	// maledetto filtro sul fondo

	public void run(String args) {

		UtilAyv.setMyPrecision();
		int userSelection1 = 0;
		int userSelection2 = 0;
		int userSelection3 = 0;
		int userSelection4 = 0;

		boolean accetta = false;
		boolean testSiemens = false;
		boolean testGe = false;

		int[] vetXUpperLeftCornerRoiGels = new int[N_GELS];
		int[] vetYUpperLeftCornerRoiGels = new int[N_GELS];
		double dimPixel = 0;
		double filtroFondo = 0;

		double kMediaFiltroFondo = 3.0;
		double kDevStFiltroFondo = 3.0;

		InputOutput io = new InputOutput();

		ResultsTable rt = null;
		ResultsTable rt11 = null;
		String[] info1 = null;
		String[] info11 = null;

		//
		// nota bene: le seguenti istruzioni devono essere all'inizio, in questo
		// modo il messaggio viene emesso, altrimenti si ha una eccezione
		//
//		try {
//			Class.forName("utils.IW2AYV");
//		} catch (ClassNotFoundException e) {
//			IJ.error("ATTENZIONE, manca il file iw2ayv_xxx.jar");
//			return;
//		}

		// ----------------------------------------------------------------------------
		if (IJ.versionLessThan("1.43k"))
			return;
		// -----------------------------
		Count c1 = new Count();
		if (!c1.jarCount("iw2ayv_"))
			return;
		// -----------------------------
		String className = this.getClass().getName();
		String user1 = System.getProperty("user.name");
		// -----------------------------
		TableCode tc1 = new TableCode();
		String[][] tabCodici = tc1.loadMultipleTable("codici", ".csv");
		String iw2ayv1 = tc1.nameTable("codici", "csv");
		String iw2ayv2 = tc1.nameTable("expand", "csv");
		// -----------------------------
		VERSION = user1 + ":" + className + "build_" + MyVersion.getVersion() + ":iw2ayv_build_"
				+ MyVersionUtils.getVersion() + ":" + iw2ayv1 + ":" + iw2ayv2;
		// -----------------------------
		// directory dati, dove vengono memorizzati ayv.txt e Results1.xls
		String fileDir = Prefs.get("prefer.string1", "none");
		// -----------------------------
		int nTokens = new StringTokenizer(args, "#").countTokens();
		boolean autoCalled = false;
		boolean manualCalled = false;
		if (nTokens > 0) {
			autoCalled = true;
			manualCalled = false;
		} else {
			autoCalled = false;
			manualCalled = true;
		}

		String[] path1 = new String[MAX_IMAGES];

//		TableCode tc1 = new TableCode();

		selftest = false;
		boolean retry = false;
		bstep = false;
		boolean abort = false;

		String defaultVetXUpperLeftCornerRoiGels = null;
		String defaultVetYUpperLeftCornerRoiGels = null;
		AboutBox ab = new AboutBox();
		if (manualCalled) {
			retry = true;
			do {
				userSelection1 = UtilAyv.userSelectionManual(VERSION, TYPE);
				switch (userSelection1) {
				case ABORT:
					ab.close();
					retry = false;
					return;
				case 2:
					ab.about("Controllo T1 e  T2", MyVersion.CURRENT_VERSION);
					// ab.about("Controllo T1 e T2", this.getClass());
					retry = true;
					break;
				case 3:
					selftest = true;
					retry = false;
					break;
				case 4:
					bstep = true;
					retry = false;
					break;
				case 5:
					retry = false;
					break;
				}
			} while (retry);
			ab.close();

			if (selftest) {
				if (!io.checkJar(TEST_FILE)) {
					UtilAyv.noTest2();
					return;
				} else {
					userSelection2 = UtilAyv.siemensGe();
					switch (userSelection2) {
					case 1:
						testSiemens = false;
						testGe = true;
						break;
					case 2:
						testSiemens = true;
						testGe = false;
						break;
					}
				}
			}
			if (!selftest) {
				typeT2 = false;
				userSelection1 = ButtonMessages.ModalMsg("Cosa vuoi elaborare?   <03>", "T1", "T2");
				switch (userSelection1) {
				case 1:
					typeT2 = true;
					break;
				case 2:
					typeT2 = false;
					break;
				}

				int num = 0;
				boolean endsel = false;
				String path0 = "";

				do {
					path0 = UtilAyv.imageSelection("SELEZIONARE IMMAGINE...");
					if (path0 == null)
						return;
					path1[num] = path0;
					num++;
					nTokens = num;
					userSelection2 = ButtonMessages.ModelessMsg("Finito ?    <04>", "SELEZIONA", "FINE");
					if (userSelection2 == 1)
						endsel = true;
				} while (!endsel);
			}
		}
		String home1 = "";

		if (testSiemens) {
			home1 = findTestImages();
			defaultVetXUpperLeftCornerRoiGels = X_ULC_ROI_TESTSIEMENS;
			defaultVetYUpperLeftCornerRoiGels = Y_ULC_ROI_TESTSIEMENS;
			vetXUpperLeftCornerRoiGels = UtilAyv.getPos(defaultVetXUpperLeftCornerRoiGels);
			vetYUpperLeftCornerRoiGels = UtilAyv.getPos(defaultVetYUpperLeftCornerRoiGels);
			MyLog.logVector(vetXUpperLeftCornerRoiGels, "vetXUpperLeftCornerRoiGels");
			MyLog.logVector(vetYUpperLeftCornerRoiGels, "vetYUpperLeftCornerRoiGels");
			MyLog.waitHere();

			for (int i1 = 0; i1 < T2_TEST_IMAGES; i1++) {
				if (i1 < SINGLE_DIGIT)
					path1[i1] = home1 + "/T2MA_0" + (i1 + 1) + "testP2";
				else
					path1[i1] = home1 + "/T2MA_" + (i1 + 1) + "testP2";
			}
			nTokens = T2_TEST_IMAGES;
			typeT2 = true;
		}
		if (testGe) {
			home1 = findTestImages();
			defaultVetXUpperLeftCornerRoiGels = X_ULC_ROI_TESTGE;
			defaultVetYUpperLeftCornerRoiGels = Y_ULC_ROI_TESTGE;
			vetXUpperLeftCornerRoiGels = UtilAyv.getPos(defaultVetXUpperLeftCornerRoiGels);
			vetYUpperLeftCornerRoiGels = UtilAyv.getPos(defaultVetYUpperLeftCornerRoiGels);

			for (int i1 = 0; i1 < T1_TEST_IMAGES; i1++) {
				path1[i1] = home1 + "/HT1A2_0" + (i1 + 1) + "testP2";
			}
			nTokens = T1_TEST_IMAGES;
			typeT2 = true;
		}
		if (autoCalled) {
			retry = false;
			do {
				userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE);
				switch (userSelection1) {
				case ABORT:
					ab.close();
					return;
				case 2:
					// ab.about("Controllo T1 e T2", this.getClass());
					ab.about("Controllo T1 e  T2", MyVersion.CURRENT_VERSION);
					retry = true;
					break;
				case 3:
					bstep = true;
					retry = false;
					break;
				case 4:
					bstep = false;
					retry = false;
					break;
				}
			} while (retry);
			ab.close();
		}
		String[] path2 = new String[nTokens];
		int[] vetRiga = new int[nTokens];

		String[] vetPath1 = new String[nTokens];

		if (manualCalled) {
			if (nTokens == 1) {
				ButtonMessages.ModelessMsg("Non e' possibile selezionare solo una immagine    <06>", "CHIUDI");
				return;
			}
			for (int i1 = 0; i1 < nTokens; i1++) {
				path2[i1] = path1[i1];
			}
		}

		if (autoCalled) {
			if (bstep)
				ButtonMessages.ModelessMsg("Ricevuto=" + args, "CONTINUA");
			defaultVetXUpperLeftCornerRoiGels = X_ULC_ROI_TESTGE;
			defaultVetYUpperLeftCornerRoiGels = Y_ULC_ROI_TESTGE;

			StringTokenizer strTok = new StringTokenizer(args, "#");
			for (int i1 = 0; i1 < nTokens; i1++)
				// vetRiga1 contiene i codici multipli passati da sequenze
				vetRiga[i1] = Integer.parseInt(strTok.nextToken());
			//
			// Carico la tabella in memoria
			//
			strRiga3 = new TableSequence().loadTable(fileDir + SEQUENZE_FILE);
			String codice = TableSequence.getCode(strRiga3, vetRiga[0]);
			String cod = codice.substring(0, 2).trim();
			if (cod.equals("T2"))
				typeT2 = true;
			else
				typeT2 = false;

			for (int i1 = 0; i1 < nTokens; i1++) {
				vetPath1[i1] = TableSequence.getPath(strRiga3, vetRiga[i1]);
			}

		} else {
			if (selftest) {
				// userSelection1 = utils.ModelessMsg(VERSION
				// + " >> SELFTEST <07> ", "CONTINUA");
			}
		} // if , else

		int misure1 = UtilAyv.setMeasure(MEAN + STD_DEV);
//		String[][] info1 = ReportStandardInfo.getStandardInfo(strRiga3,
//				vetRiga1[0], tabCodici, VERSION + "_P2_", autoCalled);

		//
		// Qui si torna se la misura e' da rifare
		//
		do {
			UtilAyv.closeResultsWindow();
			IJ.showStatus("Ricordiamo il comando RestoreSelection CTRL+SHIFT+E");

			ImageStack newStack = null;
			if (manualCalled)
				newStack = stackOpener1(path2, typeT2);
			else
				newStack = stackOpener1(vetPath1, typeT2);

			if (newStack == null)
				return;
			ImagePlus imp8 = new ImagePlus("newStack", newStack);
			if (imp8 == null)
				return;
//			String[] info1 = ReportStandardInfo.getSimpleStandardInfo(vetPath1[0], imp8, tabCodici, VERSION, autoCalled);

			info1 = ReportStandardInfo.getSimpleStandardInfo(vetPath1[0], imp8, tabCodici, VERSION, autoCalled);
			info11 = ReportStandardInfo.getSimpleStandardInfo(vetPath1[0], imp8, tabCodici, VERSION, autoCalled);

			UtilAyv.showImageMaximized(imp8);

			int width = imp8.getWidth();
			int height = imp8.getHeight();

			int Rows = ReadDicom.readInt(ReadDicom.readDicomParameter(imp8, DICOM_ROWS));
			int Columns = ReadDicom.readInt(ReadDicom.readDicomParameter(imp8, DICOM_COLUMNS));

			dimPixel = ReadDicom
					.readDouble(ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp8, DICOM_PIXEL_SPACING), 2));

			int roi_diam = (int) ((double) ROI_DIAM * DIM_PIXEL_FOV_220 / dimPixel);

			Roi[] vetRoi = new Roi[N_GELS];
			String saveVetXUpperLeftCornerRoiGels = Prefs.get("prefer.p2rmnGx", defaultVetXUpperLeftCornerRoiGels);
			String saveVetYUpperLeftCornerRoiGels = Prefs.get("prefer.p2rmnGy", defaultVetYUpperLeftCornerRoiGels);

			if (selftest) {
				saveVetXUpperLeftCornerRoiGels = defaultVetXUpperLeftCornerRoiGels;
				saveVetYUpperLeftCornerRoiGels = defaultVetYUpperLeftCornerRoiGels;
			}

			if (!selftest) {
				vetXUpperLeftCornerRoiGels = UtilAyv.getPos2(saveVetXUpperLeftCornerRoiGels, Columns);

				vetYUpperLeftCornerRoiGels = UtilAyv.getPos2(saveVetYUpperLeftCornerRoiGels, Rows);
			}

			// MyLog.logVector(vetXUpperLeftCornerRoiGels,
			// "vetXUpperLeftCornerRoiGels");
			// MyLog.waitHere();
			// MyLog.logVector(vetYUpperLeftCornerRoiGels,
			// "vetXUpperLeftCornerRoiGels");
			// MyLog.waitHere("VALORI LETTI DA PREFERENZE");

			for (int i1 = 0; i1 < vetRoi.length; i1++) {
				if (!selftest) {
					do {

						// inserisco un controllo per impedire che si possa
						// avere una roi al di fuori dell'immagine
						int xRoi = vetXUpperLeftCornerRoiGels[i1];
						int yRoi = vetYUpperLeftCornerRoiGels[i1];
						if (xRoi < 0)
							xRoi = Columns / 2;
						if (xRoi + roi_diam > Columns)
							xRoi = Columns / 2;
						if (yRoi < 0)
							yRoi = Rows / 2;
						if (yRoi + roi_diam > Rows)
							yRoi = Rows / 2;

//						imp8.setRoi(new OvalRoi(xRoi, yRoi, roi_diam, roi_diam, imp8));
						imp8.setRoi(new OvalRoi(xRoi, yRoi, roi_diam, roi_diam));

						imp8.updateAndDraw();

						userSelection1 = ButtonMessages.ModelessMsg("Posizionare ROI su GEL" + (i1 + 1)
								+ "  e premere Accetta, se l'immagine NON E'ACCETTABILE premere <ANNULLA> per passare alle successive     <08>",
								"ACCETTA", "RIDISEGNA", "<ANNULLA>");

						// MyLog.waitHere("userSelection1= " + userSelection1);

						if (userSelection1 == 1)
							abort = true;

//						userSelection1 = ButtonMessages.ModelessMsg(
//								"Posizionare ROI su GEL" + (i1 + 1) + "  e premere Accetta      <08>", "ACCETTA",
//								"RIDISEGNA");

					} while (userSelection1 == 2);
				} else {
//					if (abort) {
//						MyLog.waitHere("abbort 1");
//						break;
//					}
					imp8.setRoi(new OvalRoi(vetXUpperLeftCornerRoiGels[i1], vetYUpperLeftCornerRoiGels[i1], roi_diam,
							roi_diam));
					imp8.updateAndDraw();
					// MyLog.waitHere("verifica posizione");
					// if (bstep)
					// userSelection1 = utils.ModelessMsg(
					// "Test non modificare <40>", "CONTINUA");
				}
				if (abort) {
					break;
				}
				ImageStatistics stat1 = imp8.getStatistics();
				vetXUpperLeftCornerRoiGels[i1] = (int) stat1.roiX;
				vetYUpperLeftCornerRoiGels[i1] = (int) stat1.roiY;
				vetRoi[i1] = imp8.getRoi();
			}

			if (abort)
				break;

			saveVetXUpperLeftCornerRoiGels = UtilAyv.putPos2(vetXUpperLeftCornerRoiGels, Columns);
			saveVetYUpperLeftCornerRoiGels = UtilAyv.putPos2(vetYUpperLeftCornerRoiGels, Rows);

			// MyLog.waitHere("saveVetXUpperLeftCornerRoiGels =
			// "+saveVetXUpperLeftCornerRoiGels);
			// MyLog.waitHere("saveVetYUpperLeftCornerRoiGels =
			// "+saveVetYUpperLeftCornerRoiGels);

			// saveVetXUpperLeftCornerRoiGels = "";
			// saveVetYUpperLeftCornerRoiGels = "";
			// for (int i1 = 0; i1 < vetXUpperLeftCornerRoiGels.length; i1++) {
			// saveVetXUpperLeftCornerRoiGels = saveVetXUpperLeftCornerRoiGels
			// + vetXUpperLeftCornerRoiGels[i1] + ";";
			// saveVetYUpperLeftCornerRoiGels = saveVetYUpperLeftCornerRoiGels
			// + vetYUpperLeftCornerRoiGels[i1] + ";";
			// }

			Prefs.set("prefer.p2rmnGx", saveVetXUpperLeftCornerRoiGels);
			Prefs.set("prefer.p2rmnGy", saveVetYUpperLeftCornerRoiGels);

			// ROI sul fondo

			int xRoiFondo = Rows - DIAM_ROI_FONDO - 1;
			int yRoiFondo = Columns - DIAM_ROI_FONDO - 1;
			ImageStatistics statFondo = ImageUtils.backCalc(xRoiFondo, yRoiFondo, DIAM_ROI_FONDO, imp8, bstep, true,
					selftest);
			double mediaFondo = statFondo.mean;
			double dsFondo = statFondo.stdDev;
			filtroFondo = mediaFondo * kMediaFiltroFondo + dsFondo * kDevStFiltroFondo;

			// MyLog.waitHere("xRoiFondo = " + xRoiFondo + " yRoiFondo= "
			// + yRoiFondo + " diam= " + DIAM_ROI_FONDO + "mediaFondo= "
			// + mediaFondo + " dsFondo= " + dsFondo + " filtroFondo= "
			// + filtroFondo);

			bMap = new int[width * height];
			int offset;
			for (int y = 0; y < height; y++) {
				offset = y * width;
				for (int x = 0; x < width; x++) {
					bMap[x + offset] = 0;
					for (int i1 = 0; i1 < vetRoi.length; i1++)
						if (vetRoi[i1].contains(x, y))
							bMap[x + offset] = 1;
				}
			}
			int[] imaID = doCalculation(newStack, filtroFondo);
			if (imaID == null)
				ButtonMessages.ModelessMsg("errore p2_rmn riga 523", "continua");

			double[] medGels = new double[vetXUpperLeftCornerRoiGels.length];
			double[] devGels = new double[vetXUpperLeftCornerRoiGels.length];

			for (int i1 = 0; i1 < vetXUpperLeftCornerRoiGels.length; i1++) {
				ImagePlus imp10 = WindowManager.getImage(imaID[0]);
				imp10.setRoi(new OvalRoi(vetXUpperLeftCornerRoiGels[i1], vetYUpperLeftCornerRoiGels[i1], roi_diam,
						roi_diam));
				imp10.updateAndDraw();
				ImageStatistics stat10 = imp10.getStatistics();
				medGels[i1] = stat10.mean;
				devGels[i1] = stat10.stdDev;
			}
			//
			// qui potrei anche chiudere le immagini
			//
//			String t1 = "TESTO          ";
//			ResultsTable rt = ReportStandardInfo.putStandardInfoRT_new(info1);

//			rt = ReportStandardInfo.putSimpleStandardInfoRT_new(info1);

			if (typeT2)
				info1[0] = "T2___";
			else
				info1[0] = "T1___";

			if (!abort) {
				// rt = ReportStandardInfo.abortResultTable_P2(info1);

				// } else {
				rt = ReportStandardInfo.putSimpleStandardInfoRT_new(info1);
				rt.showRowNumbers(true);

				int col = 0;

				String t1 = "TESTO";
				String s2 = "media";
				String s3 = "devstan";
				String s4 = "roi_x";
				String s5 = "roi_y";
				String s6 = "roi_b";
				String s7 = "roi_h";

				/**
				 * rt.setHeading(++col, t1); rt.setHeading(++col, "media"); rt.setHeading(++col,
				 * "devstan"); rt.setHeading(++col, "roi_x"); rt.setHeading(++col, "roi_y");
				 * rt.setHeading(++col, "roi_b"); rt.setHeading(++col, "roi_h");
				 **/

				int gelNumber = 0;
				for (int i1 = 0; i1 < vetRoi.length; i1++) {
					gelNumber++;
					if (gelNumber > 1)
						rt.incrementCounter();
					if (gelNumber == 12)
						gelNumber = 14; // al posto 12 abbiamo il gel 14

					rt.addValue(t1, "Gel_" + gelNumber);
					rt.addValue(s2, medGels[i1]);
					rt.addValue(s3, devGels[i1]);
					rt.addValue(s4, vetXUpperLeftCornerRoiGels[i1]);
					rt.addValue(s5, vetYUpperLeftCornerRoiGels[i1]);
					rt.addValue(s6, roi_diam);
					rt.addValue(s7, roi_diam);
				}
				rt.show("Results");
			}
			// MyLog.waitHere();
			//
			// MyLog.waitHere("85, 29, 20, 20");

			if (autoCalled) {
				userSelection3 = ButtonMessages.ModelessMsg("Accettare il risultato delle misure?     <11>", "ACCETTA",
						"RIFAI");

				// MyLog.waitHere("userSelection3= " + userSelection3);

				switch (userSelection3) {
				case 1:
					UtilAyv.cleanUp();
					accetta = false;
					break;
				case 2:
					accetta = true;
					break;
				}
			}
			// MyLog.waitHere("accetta1= " + accetta);

			if (manualCalled) {
				if (selftest) {
					if (testSiemens)
						testSymphony(medGels[0], devGels[0], medGels[1], devGels[1], medGels[2], devGels[2]);

					if (testGe)
						testGe(medGels[0], devGels[0], medGels[1], devGels[1], medGels[2], devGels[2]);
				} else {
					userSelection4 = ButtonMessages.ModelessMsg(
							"Fine programma, in modo STAND-ALONE salvare A MANO la finestra Risultati    <12>",
							"CONTINUA");
					System.gc();

				}
				accetta = true;
			}
			// MyLog.waitHere("accetta2= " + accetta);

		} while (!accetta); // do

		// MyLog.waitHere("scrittura1");

		if (abort) {
			// MyLog.waitHere("abort");
			if (typeT2)
				info1[0] = "T2___";
			else
				info1[0] = "T1___";

			
			rt = ReportStandardInfo.abortResultTable_P2(info1);
		}

		//
		// Salvataggio dei risultati
		//

		if (autoCalled) {

			// MyLog.waitHere("scrittura2");

			//
			// per la scrittura utilizzo questa routine, comune agli altri plugins
			//
			UtilAyv.saveResults(vetRiga, fileDir, strRiga3, rt);

//			try {
//				mySaveAs(fileDir + MyConst.TXT_FILE);
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//
//			// IJ.run("Excel...", "select...=[" + fileDir + XLS_FILE + "]");
//			TableSequence lr = new TableSequence();
//			for (int i1 = 0; i1 < nTokens; i1++)
//				lr.putDone(strRiga3, vetRiga[i1]);
//			lr.writeTable(fileDir + SEQUENZE_FILE, strRiga3);
		}

		UtilAyv.resetResultsTable();
		UtilAyv.resetMeasure(misure1);

		// InputOutput.deleteDir(new File(TEST_DIRECTORY));
		// if (autoCalled)
		UtilAyv.afterWork();

		// UtilAyv.cleanUp();
	} // close run

	/**
	 * Saves this ResultsTable as a tab or comma delimited text file. The table is
	 * saved as a CSV (comma-separated values) file if 'path' ends with ".csv".
	 * Displays a file save dialog if 'path' is empty or null. Does nothing if the
	 * table is empty.
	 */
	public static void mySaveAs(String path) throws IOException {

		ResultsTable rt = ResultsTable.getResultsTable();
		if (rt.getCounter() == 0)
			return;
		PrintWriter pw = null;
		boolean append = true;
		FileOutputStream fos = new FileOutputStream(path, append);
		BufferedOutputStream bos = new BufferedOutputStream(fos);
		pw = new PrintWriter(bos);
		String headings = rt.getColumnHeadings();
		pw.println("## " + headings);
		for (int i = 0; i < rt.getCounter(); i++)
			pw.println(rt.getRowAsString(i));
		pw.close();
	}

	/**
	 * crea uno stack contenente le immagini di vetDir1, in ordine crescente
	 * rispetto al parametro selezionato
	 * 
	 * @param vetDir1 vettore coi path immagini
	 * @param typeT2  selezione parametro ordinamento
	 * @return stack con le immagini
	 * 
	 *         by Alberto Duina
	 */
	public ImageStack stackOpener1(String[] vetDir1, boolean typeT2) {

		String aux1;
		String aux2;

		ImageStack newStack1 = null;
		int count1 = 0;
		String[] para1 = new String[vetDir1.length];
		for (int i1 = 0; i1 < vetDir1.length; i1++) {
			ImagePlus imp1 = new Opener().openImage(vetDir1[i1]);
			if (typeT2)
				aux1 = DICOM_ECHO_TIME;
			else
				aux1 = DICOM_INVERSION_TIME;
			aux2 = ReadDicom.readDicomParameter(imp1, aux1);
			if (aux2.compareTo("MISS") == 0) {
				ButtonMessages.ModelessMsg("Errore selezione tipo immagine T1 o T2 " + aux1, "CONTINUA");
				return (null);
			}
			para1[i1] = aux2;
		}

		/** bubblesort start */
		double d1;
		double d2;
		String temp1;
		for (int i1 = 0; i1 < vetDir1.length; i1++) {
			for (int i2 = 0; i2 < vetDir1.length - 1 - i1; i2++) {
				d1 = ReadDicom.readDouble(para1[i2]);
				d2 = ReadDicom.readDouble(para1[i2 + 1]);
				if (d1 > d2) {
					temp1 = para1[i2];
					para1[i2] = para1[i2 + 1];
					para1[i2 + 1] = temp1;

					temp1 = vetDir1[i2];
					vetDir1[i2] = vetDir1[i2 + 1];
					vetDir1[i2 + 1] = temp1;
				}
			}
		}
		/** bubblesort end */
		for (int i1 = 0; i1 < vetDir1.length; i1++) {
			count1++;
			ImagePlus imp1 = new Opener().openImage(vetDir1[i1]);
			if (count1 == 1) {
				int Rows = ReadDicom.readInt(ReadDicom.readDicomParameter(imp1, DICOM_ROWS));
				int Columns = ReadDicom.readInt(ReadDicom.readDicomParameter(imp1, DICOM_COLUMNS));
				cal8 = imp1.getCalibration();
				newStack1 = new ImageStack(Rows, Columns);
			}
			String label1 = imp1.getTitle();
			String info2 = (String) imp1.getProperty("Info");
			if (info2 != null)
				label1 += "\n" + info2;
			ImageProcessor ip1 = imp1.getProcessor();
			ImageProcessor ip2 = ip1.duplicate();
			newStack1.addSlice(label1, ip2);
		}
		return (newStack1);
	} // stackOpener1

	/**
	 * dispone il contenuto dei pixel nella tabella in formato double [][][][].
	 * Nellla tabella abbiamo: tabella[rep][slice][x][y] in cui slice � sempre 0
	 * (usato per compatibilit� con le librerie che sono fatte per processare dei
	 * volumi.
	 * 
	 * @param stack1 stack di immagini da processare
	 * @return valori dei pixel nel formato double[a][b][c][d], in cui a=posiz.
	 *         immagine nello stack b=dummy sempre 0, c=coord. x pixel, d=coord. y
	 *         pixel
	 * 
	 *         by Alberto Duina
	 */

	public double[][][][] Alimentatore(ImageStack stack1) {

		String name1 = "";
		ImagePlus imp1 = new ImagePlus(name1, stack1);

		int[] dims = new int[ARRAY_DIMENSIONS];
		dims[0] = imp1.getHeight();
		dims[1] = imp1.getWidth();
		dims[2] = 1;
		dims[3] = imp1.getStackSize();
		int slice = 0;
		double pixValue = 0;

		double[][][][] ret = new double[dims[3]][dims[2]][dims[1]][dims[0]];

		for (int rep = 0; rep < ret.length; rep++) {
			ImageProcessor ip1 = stack1.getProcessor(rep + 1);
			// ip1.setCalibrationTable(cal8.getCTable());
			int width2 = ip1.getWidth();
			int height2 = ip1.getHeight();
			int bitDepth = imp1.getBitDepth();
			short[] sdata = null;
			if (bitDepth == 16) {
				sdata = (short[]) ip1.getPixels();
			} else {
				ImageProcessor ip2 = ip1.convertToShort(false);
				sdata = (short[]) ip2.getPixels();
			}
			for (int y = 0; y < height2; y++) {
				for (int x = 0; x < width2; x++) {
					// pixValue serve per leggere correttamente i valori delle
					// immagini signed (calibrate, GE)
					pixValue = (double) cal8.getRawValue(sdata[x + y * width2]);
					ret[rep][slice][x][y] = pixValue;
				}
			}
		}
		return (ret);
	} // Alimentatore

	/**
	 * legge un valore nell'header delle immagini contenute nello stack e lo
	 * restituisce in un vettore
	 * 
	 * @param stack1    stack immagini da processare
	 * @param userinput parametro da leggere
	 * @return vettore col TR / TI
	 * 
	 *         by Alberto Duina
	 */
	private double[] getTRVals(ImageStack stack1, String userinput) {
		//
		// attenzione nel caso del T1, viene chiamato TR ma in realt� � il TI
		//
		String attribute = "???";
		String value = "???";

		int nFrames = stack1.getSize();
		double[] ret = new double[nFrames];
		for (int w1 = 0; w1 < nFrames; w1++) {
			String header = stack1.getSliceLabel(w1 + 1);
			if (header != null) {
				int idx1 = header.indexOf(userinput);
				int idx2 = header.indexOf(":", idx1);
				int idx3 = header.indexOf("\n", idx2);
				if (idx1 >= 0 && idx2 >= 0 && idx3 >= 0) {
					try {
						attribute = header.substring(idx1 + 9, idx2).trim();
						value = header.substring(idx2 + 1, idx3).trim();

						ret[w1] = ReadDicom.readDouble(value);
					} catch (Throwable e) {

						ButtonMessages.ModelessMsg("getTRVals error value >> " + value, "CONTINUA");
					}
				} else {
					attribute = "MISSING";
					ButtonMessages.ModelessMsg("getTRVals error attribute >> " + attribute + userinput, "CONTINUA");
				}
			} else {
				attribute = "HEADERNULL";
				ButtonMessages.ModelessMsg("getTRVals error attribute >> " + attribute, "CONTINUA");
			}

		}
		return ret;

	}

	/**
	 * main che effettua la minimizzazione
	 * 
	 * @param stack4 stack contenente le immagini
	 * @return ID delle immagini da passare a WindowManager
	 * 
	 *         by Karl Schmidt, (modified version)
	 */
	public int[] doCalculation(ImageStack stack4, double filtroFondo) {
		double[][][][] t1_map = null;
		double[][][][] s0_map = null;
		double[][][][] r2_map = null;

		double progress = 0;
		double[] tr_vals = null;
		double[] sn_vals = null;
		double[] tr2_vals = null;
		double[] sn2_vals = null;

		int[] imaID = null;
		int rig = 0;
		int col = 0;
		int offset;
		int shot = 0;
		int shotInterval = 0;

		try {
			double[][][][] scan = Alimentatore(stack4);
			int slices = scan[0].length;
			int width2 = scan[0][0].length;
			int height2 = scan[0][0][0].length;
			int reps = scan.length;

			SimplexBasedRegressor simplexregressor;

			if (DEBUG2)
				IJ.log("sono in doCalculation");
			if (!typeT2) {
				// te values

				tr_vals = getTRVals(stack4, DICOM_INVERSION_TIME);
				sn_vals = new double[reps];
				IJ.showStatus("Calculating T1 map");
				t1_map = new double[1][slices][width2][height2];
				s0_map = new double[1][slices][width2][height2];
				r2_map = new double[1][slices][width2][height2];
				simplexregressor = new SimplexBasedRegressor();
			} else {
				tr_vals = getTRVals(stack4, DICOM_ECHO_TIME);
				// MyLog.logVector(tr_vals, "tr_vals");
				sn_vals = new double[reps];
				IJ.showStatus("Calculating T2 map");
				t1_map = new double[1][slices][width2][height2];
				s0_map = new double[1][slices][width2][height2];
				r2_map = new double[1][slices][width2][height2];
				simplexregressor = new SimplexBasedRegressor();
			}
			// fit T2 and So
			shot = 0;
			shotInterval = 100;

			for (int s = 0; s < slices; s++) {
				for (int y = 0; y < height2; y++) {
					offset = y * width2;
					rig = y;
					progress = (double) (100 * (s * width2 * height2 + y * width2) / (width2 * height2 * slices));
					IJ.showStatus("riga:" + y);
					IJ.showProgress(progress);
					for (int x = 0; x < width2; x++) {
						if ((x == 47 || x == 46 || x == 48) && (y == 88 || y == 87 || y == 89)) {
							if (DEBUG2 == true)
								debug1 = true;
						} else {
							debug1 = false;
						}

						IJ.showProgress(progress);
						col = x;
						// for each pixel, collect values for each te
						for (int r = 0; r < reps; r++) {
							sn_vals[r] = scan[r][s][x][y];
							if (sn_vals[r] == 0)
								sn_vals[r] = 1.0d;
						}

						double[] regressed;
						// if (!typeT2 || (sn_vals.length < 10)) {
						if (!typeT2) {
							if (bMap[x + offset] == 1) {
								regressed = simplexregressor.regressT1InversionRecovery(tr_vals, sn_vals, 700.0d);

								t1_map[0][s][x][y] = regressed[0];
								s0_map[0][s][x][y] = regressed[1];
								r2_map[0][s][x][y] = regressed[2];
							} else {
								t1_map[0][s][x][y] = 0;
								s0_map[0][s][x][y] = 0;
								r2_map[0][s][x][y] = 0;
							}
						} else {
							if (bMap[x + offset] == 1) {

								int cut = 0;
								double filtroFondo2 = filtroFondo;
								// ##############################################
								// applicazione del filtro sul fondo ai segnali
								// in ingresso, ma solo se abbiamo piu' di 10
								// immagini
								// #############################################

								if (sn_vals.length < 10)
									filtroFondo2 = -32768;
								if (selftest)
									filtroFondo2 = -32768;
								for (int i1 = 0; i1 < sn_vals.length; i1++) {
									if (sn_vals[i1] > filtroFondo2) {
										cut = i1;
										// break;
									}
								}

								cut++;
								String bb = "";
								tr2_vals = new double[cut];
								sn2_vals = new double[cut];
								for (int i1 = 0; i1 < cut; i1++) {
									tr2_vals[i1] = tr_vals[i1];
									sn2_vals[i1] = sn_vals[i1];
								}

								// ########## debug ##########
								boolean xx = false;
								if (shot++ > shotInterval) {
									shot = 0;
									xx = true;
								} else
									xx = false;
								xx = false; // debug spento
								// if (xx) {
								if (debug1) {

									String aa = "";
									for (int i1 = 0; i1 < tr_vals.length; i1++) {
										aa = aa + tr_vals[i1] + "  ";
										bb = bb + sn_vals[i1] + "  ";
									}
									IJ.log("-----------------");
									IJ.log("tr_vals= " + aa);
									IJ.log("sn_vals= " + bb);
									IJ.log("filtroFondo2= " + filtroFondo2);
									IJ.log("cut= " + cut);
									aa = "";
									bb = "";
									for (int i1 = 0; i1 < tr2_vals.length; i1++) {
										aa = aa + tr2_vals[i1] + "  ";
										bb = bb + sn2_vals[i1] + "  ";
									}
									IJ.log("tr2_vals= " + aa);
									IJ.log("sn2_vals= " + bb);
								}
								// ###################################

								//
								// impongo che i valori su cui effettuare il fit
								// siano almeno 4 ..... insisto che dissento
								// profondamente su filtro del fondo
								// ... quasi un dissenteria!!
								//
								if (cut >= 4) {

									// regressed = simplexregressor.regressT2(
									// tr2_vals, sn2_vals, 150.0d, debug1);
									regressed = simplexregressor.regressT2(tr2_vals, sn2_vals, 150.0d, false);
									if (debug1) {
										IJ.log("T2calculated= " + regressed[0]);
										IJ.log("S0calculated= " + regressed[1]);
										IJ.log("R2calculated= " + regressed[2]);
									}
									t1_map[0][s][x][y] = regressed[0];
									s0_map[0][s][x][y] = regressed[1];
									r2_map[0][s][x][y] = regressed[2];
								} else {
									t1_map[0][s][x][y] = 0;
									s0_map[0][s][x][y] = 0;
									r2_map[0][s][x][y] = 0;
								}

							} else {
								t1_map[0][s][x][y] = 0;
								s0_map[0][s][x][y] = 0;
								r2_map[0][s][x][y] = 0;
							}
						}
						// IJ.showProgress(progress);
					}
				}

				IJ.showStatus("Finished");
				// send the images back to imagej
				String name1 = "none";

				imaID = new int[3];
				if (!typeT2)
					name1 = "T1_map_secs";
				else
					name1 = "T2_map_secs";
				imaID[0] = takeImage(name1, t1_map);
				imaID[1] = takeImage("S0_map", s0_map);
				imaID[2] = takeImage("R^2_map", r2_map);
				scan = null;
				// System.gc();
			}
		} catch (Exception e) {
			IJ.log("riga:" + rig + "  pixel:" + col);
			if (sn_vals == null)
				IJ.log("sn_vals==null");
			IJ.log("sn_vals=" + sn_vals.length);
			for (int i1 = 0; i1 < sn_vals.length; i1++)
				IJ.log("" + sn_vals[i1]);
			IJ.log("In DoCalculation eccezione: " + e);
		}
		return (imaID);
	} // doCalculation

	/**
	 * crea uno stack a 32 bit in cui mette i risultati
	 * 
	 * @param name   nome da assegnare allo stack
	 * @param data4d matrice risultati elaborazione
	 * @return ID immagine
	 * 
	 *         by Karl Schmidt, (modified version)
	 */
	public int takeImage(String name, double[][][][] data4d) {
		// create a new stack of 32 bit floating point pixels
		ImagePlus newimp = NewImage.createFloatImage(name, data4d[0][0].length, data4d[0][0][0].length,
				data4d.length * data4d[0].length, NewImage.FILL_BLACK);
		ImageStack stack = newimp.getStack();

		// populate the slices of the new stack with the transform
		for (int rep = 0; rep < data4d.length; rep++) {
			for (int slice = 0; slice < data4d[0].length; slice++) {
				// set the processor to the 2d data array
				setPixels(stack, data4d[rep][slice], rep * data4d[0].length + 1 + slice, ImagePlus.GRAY32);
			}
		}

		newimp.setTitle(name);
		newimp.show();

		// set this images dimensions
		int[] dims = new int[ARRAY_DIMENSIONS];
		dims[0] = data4d[0][0].length;
		dims[1] = data4d[0][0][0].length;
		dims[2] = data4d[0].length;
		dims[3] = data4d.length;

		setImageDims(newimp.getID(), dims);
		newimp.updateAndDraw();
		newimp.getProcessor().resetMinAndMax();
		// IJ.showMessage("Finito takeImage");
		return newimp.getID();
	}

	/**
	 * inserisce nei pixel delle immagini dello stack i valori della matrice dati
	 * 
	 * @param stack stack immagini
	 * @param data  dati da inserire
	 * @param slice numero immagine nello stack
	 * @param type  numero livelli colore
	 * 
	 *              by Karl Schmidt, (modified version)
	 */
	private void setPixels(ImageStack stack, double[][] data, int slice, int type) {
		int w = data.length;
		int h = data[0].length;
		ImageProcessor ip = stack.getProcessor(slice);
		if (type == ImagePlus.GRAY8) {
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					((byte[]) ip.getPixels())[x + y * w] = (byte) data[x][y];
				}
			}
		} else if (type == ImagePlus.GRAY16) {
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					((short[]) ip.getPixels())[x + y * w] = (short) data[x][y];
				}
			}
		} else if (type == ImagePlus.GRAY32) {
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					((float[]) ip.getPixels())[x + y * w] = (float) data[x][y];
				}
			}
		} else if (type == ImagePlus.COLOR_256 || type == ImagePlus.COLOR_RGB) {
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					((int[]) ip.getPixels())[x + y * w] = (int) data[x][y];
				}
			}
		}

	}

	/**
	 * sets the number of slices for a particular image in system memory for
	 * retreival later by other plugins [width, height, slices, reps]
	 * 
	 * @param id   ID dell'immagine
	 * @param dims dimensioni matrice immagine
	 */
	public void setImageDims(int id, int[] dims) {
		System.setProperty("imagej.imgslicenum." + id, "" + dims[2]);
	}

	private void testSymphony(double medGel1, double devGel1, double medGel2, double devGel2, double medGel3,
			double devGel3) {

		//
		// aggiornato 16 aprile 2008 1.40e
		//
		boolean testok = true;
		double rightValue = 58.592281981359555;
		if (medGel1 != rightValue) {
			IJ.log("medGel1 ERRATA " + medGel1 + " anzich� " + rightValue);
			testok = false;
		}
		rightValue = 3.866370693178298;
		if (devGel1 != rightValue) {
			IJ.log("devGel1 ERRATA " + devGel1 + " anzich� " + rightValue);
			testok = false;
		}
		rightValue = 73.95285294931146;
		if (medGel2 != rightValue) {
			IJ.log("medGel2 ERRATA " + medGel2 + " anzich� " + rightValue);
			testok = false;
		}
		rightValue = 3.6103799208252223;
		if (devGel2 != rightValue) {
			IJ.log("devGel2 ERRATA " + devGel2 + " anzich� " + rightValue);
			testok = false;
		}
		rightValue = 113.45177367970913;
		if (medGel3 != rightValue) {
			IJ.log("medGel3 ERRATA " + medGel3 + " anzich� " + rightValue);
			testok = false;
		}
		rightValue = 4.585334675446435;
		if (devGel3 != rightValue) {
			IJ.log("devGel3 ERRATA " + devGel3 + " anzich� " + rightValue);
			testok = false;
		}

		if (testok)
			ButtonMessages.ModelessMsg("Fine SelfTest TUTTO OKEY   <25>", "CONTINUA");
		else
			ButtonMessages.ModelessMsg("Fine SelfTest CON ERRORI, vedi Log   <26>", "CONTINUA");
		UtilAyv.cleanUp();

	}

	private void testGe(double medGel1, double devGel1, double medGel2, double devGel2, double medGel3,
			double devGel3) {
		//
		// aggiornato 16 aprile 2008 1.40e
		//

		boolean testok = true;

		double rightValue = 41.12338699871981;
		if (medGel1 != rightValue) {
			IJ.log("medGel1 ERRATA " + medGel1 + " anzich� " + rightValue);
			testok = false;
		}
		rightValue = 6.807091233248218;
		if (devGel1 != rightValue) {
			IJ.log("devGel1 ERRATA " + devGel1 + " anzich� " + rightValue);
			testok = false;
		}
		rightValue = 47.02839973304845;
		if (medGel2 != rightValue) {
			IJ.log("medGel2 ERRATA " + medGel2 + " anzich� " + rightValue);
			testok = false;
		}
		rightValue = 6.160207177522188;
		if (devGel2 != rightValue) {
			IJ.log("devGel2 ERRATA " + devGel2 + " anzich� " + rightValue);
			testok = false;
		}
		rightValue = 73.95455746711055;
		if (medGel3 != rightValue) {
			IJ.log("medGel3 ERRATA " + medGel3 + " anzich� " + rightValue);
			testok = false;
		}
		rightValue = 5.076411495907888;
		if (devGel3 != rightValue) {
			IJ.log("devGel3 ERRATA " + devGel3 + " anzich� " + rightValue);
			testok = false;
		}

		if (testok)
			ButtonMessages.ModelessMsg("Fine SelfTest TUTTO OKEY   <25>", "CONTINUA");
		else
			ButtonMessages.ModelessMsg("Fine SelfTest CON ERRORI, vedi Log   <26>", "CONTINUA");
		UtilAyv.cleanUp();

	}

	/**
	 * genera una directory temporanea e vi estrae le immagini di test da test2.jar
	 * 
	 * @return home1 path della directory temporanea con le immagini di test
	 */
	private String findTestImages() {
		InputOutput io = new InputOutput();

		for (int i1 = 0; i1 < T2_TEST_IMAGES; i1++) {
			if (i1 < 9)
				io.extractFromJAR(TEST_FILE, "T2MA_0" + (i1 + 1) + "testP2", "./Test2/");
			else
				io.extractFromJAR(TEST_FILE, "T2MA_" + (i1 + 1) + "testP2", "./Test2/");
		}
		for (int i1 = 0; i1 < T1_TEST_IMAGES; i1++) {
			io.extractFromJAR(TEST_FILE, "HT1A2_0" + (i1 + 1) + "testP2", "./Test2/");
		}
		String home1 = this.getClass().getResource(TEST_DIRECTORY).getPath();
		return (home1);
	} // findTestImages

} // p2rmn
