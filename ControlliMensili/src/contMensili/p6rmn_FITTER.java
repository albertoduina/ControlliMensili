package contMensili;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.gui.Roi;
import ij.io.OpenDialog;
import ij.measure.CurveFitter;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.util.Tools;
import utils.AboutBox;
import utils.ArrayUtils;
import utils.ButtonMessages;
import utils.InputOutput;
import utils.MyConst;
import utils.MyLog;
import utils.MyMsg;
import utils.MyPlot;
import utils.MyStackUtils;
import utils.MyVersionUtils;
import utils.ProfileUtils;
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
 * Analizza SPESSORE FETTA effettua 4 profili, non necessariamente orizzontali ,
 * mediati su 11 linee e su questi calcola FWHM e posizione del picco. Lavora
 * sia su singola immagine che su stack
 *
 * In questa evoluzione del programma vi sono 3 algoritmi selezionabili: il
 * primo e' la versione originale, la seconda utilizza un fit gausssiano, e la
 * terza un fit super-gaussiano (flat top). Per tutte e tre le versioni la fwhm
 * viene fatta cercando i pixel a mezza altezza (niente fwhm derivato da formule
 * x che a volte mi diventa negativo senza ragioni apparenti). Se tutto va bene
 * alla fine rimane in funzione solo il fit super-gaussiano, gli altri due
 * servono solo per confronto durante i test.
 *
 * ____29jan07 v 3.00 i dati di output sugli spessori sono ora in mm (matr/fov)
 *
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 *
 */

public class p6rmn_FITTER implements PlugIn, Measurements {

	static final int ABORT = 1;

	public static final boolean SPY = true; // attiva diagnostica e salvataggio immagini e log

	public static final boolean P6_ORIGINAL = true;
	public static final boolean P6_GAUSS = true;
	public static final boolean P6_SUPERGAUSS = true;
	// public static int preset=1;
	public static String spypath = "";
	public static String spyname = "spyname";
	public static String spylog = "";
	public static String spydir = "";
	public static int contaxx = 1;

	public static String VERSION = "SPESSORE FETTA";

	private static String TYPE1 = " >> CONTROLLO THICKNESS______________";

	private static String TYPE2 = " >> CONTROLLO THICKNESS MULTISLICE___";

	private static String fileDir = "";

//	private static boolean previous = false; // utilizzati per impulso
//	private static boolean init1 = true; // utilizzati per impulso
//	private static boolean pulse = false; // utilizzati per impulso
	private static boolean verbose = false;

	// ---------------------------"01234567890123456789012345678901234567890"

	@Override
	public void run(String args) {
		// for our phantom the nominal fwhm for thickness 2 mm is 10.3
		// for our phantom the nominal fwhm for thickness 5 mm is 25.7
		// peak position is the maximum profile position
		// manual selection of unaccettable slices (border or center (cross
		// slabs) slices

		UtilAyv.setMyPrecision();

		if (IJ.versionLessThan("1.53i")) {
			return;
		}
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
//		TableExpand tc2 = new TableExpand();
		String iw2ayv2 = tc1.nameTable("expand", "csv");

		VERSION = user1 + ":" + className + "build_" + MyVersion.getVersion() + ":iw2ayv_build_"
				+ MyVersionUtils.getVersion() + ":" + iw2ayv1 + ":" + iw2ayv2;

//		VERSION = className + "_build_" + MyVersion.getVersion() + "_iw2ayv_build_" + MyVersionUtils.getVersion();

		fileDir = Prefs.get("prefer.string1", "none");

		int nTokens = new StringTokenizer(args, "#").countTokens();
		if (nTokens == 0) {
			manualMenu(0, "");
		} else {
			autoMenu(args);
		}
		return;
	}

	public int manualMenu(int preset2, String testDirectory) {
		boolean retry = false;
		boolean step = false;
		do {
			int userSelection1 = UtilAyv.userSelectionManual(VERSION, TYPE2);
			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				retry = false;
				return 0;
			case 2:
				// new AboutBox().about("Controllo Thickness", this.getClass());
				new AboutBox().about("Controllo Thickness", MyVersion.CURRENT_VERSION);
				retry = true;
				break;
			case 3:
				retry = false;
				selfTestMenu();
				break;
			case 4:
				step = true;
				retry = false;
				// dont put break here!
			case 5:
				String[] path = manualImageSelection();
				if (path == null) {
					return 5;
				}
				boolean autoCalled = false;
				boolean verbose = true;
				boolean test = false;
				ImagePlus imp0 = UtilAyv.openImageNoDisplay(path[0], true);
				double[] oldPosition = p6rmn_COMMON.readReferences_COMMON(imp0);
				// new p6rmn_().wrapThickness(path, "0", oldPosition, autoCalled, step, verbose,
				// test);

				new p6rmn_FITTER().mainThickness(path, "0", oldPosition, autoCalled, step, verbose, test);
				UtilAyv.afterWork();
				retry = true;
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
		MyLog.appendLog(fileDir + "MyLog.txt", "p6rmn_FITTER riceve " + autoArgs);

		// the autoArgs are passed from Sequenze_
		// possibilities:
		// 1 token -1 = silentAutoTest
		// 2 tokens auto
		// 4 tokens auto
		int nTokens = new StringTokenizer(autoArgs, "#").countTokens();
		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);
		if (vetRiga[0] == -1) {
			IJ.log("selfTestSilent.p6rmn_");
			selfTestSilent();
			return 0;
		}

		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir + MyConst.SEQUENZE_FILE);

		boolean retry = false;
		boolean step = false;
		int userSelection1 = 0;
		do {
			if (nTokens == 1) {
				// userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE1);
				userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE1,
						TableSequence.getCode(iw2ayvTable, vetRiga[0]), TableSequence.getCoil(iw2ayvTable, vetRiga[0]),
						vetRiga[0] + 1, TableSequence.getLength(iw2ayvTable));
			} else {
				// userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE2);
				userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE2,
						TableSequence.getCode(iw2ayvTable, vetRiga[0]), TableSequence.getCoil(iw2ayvTable, vetRiga[0]),
						vetRiga[0] + 1, TableSequence.getLength(iw2ayvTable));
			}

			step = false;
			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				return 0;
			case 2:
				// new AboutBox().about("Controllo Thickness", this.getClass());
				new AboutBox().about("Controllo Thickness", MyVersion.CURRENT_VERSION);
				retry = true;
				break;
			case 3:
				step = true;
				retry = false;
				// dont put break here!
			case 4:
				retry = false;
				boolean verbose = true;
				boolean test = false;
				boolean autoCalled = true;
				String[] path = p6rmn_COMMON.loadPath_COMMON(autoArgs);
				ImagePlus imp0 = UtilAyv.openImageNoDisplay(path[0], true);
				double[] vetRefPosition = p6rmn_COMMON.readReferences_COMMON(imp0);
				ResultsTable rt = null;
				boolean accetta = false;
				do {
					rt = mainThickness(path, autoArgs, vetRefPosition, autoCalled, step, verbose, test);
					if (rt != null) {
						// MyLog.here("verifyResultTable");
						rt.show("Results");
						if (autoCalled && !test) {
							if (SPY) {
								accetta = true;
							} else {
								accetta = MyMsg.accettaMenu();
							}
						} else {
							if (!test) {
								accetta = MyMsg.msgStandalone();
							} else {
								accetta = test;
							}
						}
					} else {
						TableCode tc11 = new TableCode();
						String[][] tabCodici11 = tc11.loadMultipleTable("codici", ".csv");
//						int len = path.length;
						ImagePlus imp11 = null;
						String[] info11 = null;
						String[] slicePos = new String[vetRiga.length];
						for (int i1 = 0; i1 < vetRiga.length; i1++) {
							imp11 = UtilAyv.openImageNoDisplay(path[i1], true);
							info11 = ReportStandardInfo.getSimpleStandardInfo(path[i1], imp11, tabCodici11, VERSION,
									autoCalled);
							slicePos[i1] = ReadDicom.readSubstring(
									ReadDicom.readDicomParameter(imp11, MyConst.DICOM_IMAGE_POSITION), 3);
						}
						rt = ReportStandardInfo.abortResultTable_P6(info11, slicePos, nTokens);
						accetta = true;
						MyLog.here("abortResultTable");
					}
				} while (!accetta);

				UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, rt);
				if (SPY) {
					UtilAyv.saveResults(vetRiga, spydir + "\\" + spyname, iw2ayvTable, rt);
				}

				if (step) {
					MyLog.waitHere();
				}
				break;
			}
		} while (retry);
		if (SPY) {
			p6rmn_COMMON.saveLog_COMMON(spydir, "Log1.txt", SPY);
		}
		new AboutBox().close();
		UtilAyv.afterWork();
		return 0;
	}

//	public void saveLog(String path, String name) {
//		if (SPY) {
//			IJ.selectWindow("Log");
//			IJ.saveAs("text", path + "\\" + name);
//		}
//	}

	public String[] manualImageSelection() {
		boolean endsel = false;
		List<String> listPath = new ArrayList<String>();
		do {
			OpenDialog od1 = new OpenDialog("SELEZIONARE IMMAGINE...", "");
			String directory1 = od1.getDirectory();
			String name1 = od1.getFileName();
			if (name1 == null) {
				return null;
			}
			listPath.add(directory1 + name1);
			int userSelection = ButtonMessages.ModelessMsg("Finito ?", "SELEZIONA ALTRA IMMAGINE", "FINE SELEZIONE");
			if (userSelection == 1) {
				endsel = true;
			}
		} while (!endsel);
		String[] path = ArrayUtils.arrayListToArrayString(listPath);
		return path;
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
				String[] list = { "HT5A2_testP6" };
				String[] path1 = new InputOutput().findListTestImages2(MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
				double[] vetRefPosition = MyConst.P6_REFERENCE_LINE_GE;
				// String[] path={"./Test2/BT2A_testP6"};
				String autoArgs = "0";
				boolean autoCalled = false;
				boolean step = false;
				boolean verbose = false;
				boolean test = true;
				ResultsTable rt = new p6rmn_FITTER().mainThickness(path1, autoArgs, vetRefPosition, autoCalled, step,
						verbose, test);
				rt.show("Results");
				MyLog.waitHere("verifica results table");
				double[] vetResults = UtilAyv.vectorizeResults(rt);
				boolean ok = UtilAyv.verifyResults1(vetResults, referenceGe(), MyConst.P6_vetName);
				if (ok) {
					MyMsg.msgTestPassed();
				} else {
					MyMsg.msgTestFault();
				}
				UtilAyv.afterWork();

				break;
			}
			case 2:
				// Siemens
				String[] list = { "BT2A_testP6" };
				String[] path1 = new InputOutput().findListTestImages2(MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
				double[] vetRefPosition = MyConst.P6_REFERENCE_LINE_SIEMENS;
				// String[] path={"./Test2/BT2A_testP6"};
				String autoArgs = "0";
				boolean autoCalled = false;
				boolean step = false;
				boolean verbose = false;
				boolean test = true;

				ResultsTable rt = new p6rmn_FITTER().mainThickness(path1, autoArgs, vetRefPosition, autoCalled, step,
						verbose, test);

				double[] vetResults = UtilAyv.vectorizeResults(rt);
				boolean ok = UtilAyv.verifyResults1(vetResults, referenceSiemens(), MyConst.P6_vetName);
				if (ok) {
					MyMsg.msgTestPassed();
				} else {
					MyMsg.msgTestFault();
				}
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
		// Siemens
		String[] list = { "BT2A_testP6" };
		String[] path1 = new InputOutput().findListTestImages2(MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

		double[] vetRefPosition = MyConst.P6_REFERENCE_LINE_SIEMENS;
		String autoArgs = "-1";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = false;
		boolean test = true;

		ResultsTable rt = new p6rmn_FITTER().mainThickness(path1, autoArgs, vetRefPosition, autoCalled, step, verbose,
				test);

		double[] vetResults = UtilAyv.vectorizeResults(rt);
		boolean ok = UtilAyv.verifyResults1(vetResults, referenceSiemens(), MyConst.P6_vetName);
		if (ok) {
			IJ.log("Il test di p6rmn_ THICKNESS e' stato SUPERATO");
		} else {
			IJ.log("Il test di p6rmn_ THICKNESS evidenzia degli ERRORI");
		}
	}

//	public String[] loadPath(String autoArgs) {
//		String fileDir = Prefs.get("prefer.string1", "./test2/");
//		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir + MyConst.SEQUENZE_FILE);
//		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);
//		String[] path = new String[vetRiga.length];
//		for (int i1 = 0; i1 < vetRiga.length; i1++) {
//			path[i1] = TableSequence.getPath(iw2ayvTable, vetRiga[i1]);
//		}
//		return path;
//	}

//	public double[] readReferences(ImagePlus imp1) {
//		int rows = imp1.getHeight();
//		float dimPixel = ReadDicom
//				.readFloat(ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 1));
//
//		double[] vetReference = new double[4];
//		vetReference[0] = ReadDicom.readDouble(Prefs.get("prefer.p6rmnAx", "" + 50.0 / dimPixel));
//		vetReference[1] = ReadDicom.readDouble(Prefs.get("prefer.p6rmnAy", "" + 60.0 / dimPixel));
//		vetReference[2] = ReadDicom.readDouble(Prefs.get("prefer.p6rmnBx", "" + 50.0 / dimPixel));
//		vetReference[3] = ReadDicom.readDouble(Prefs.get("prefer.p6rmnBy", "" + 215.0 / dimPixel));
//		if (p6rmn_COMMON.interdiction_COMMON(vetReference, rows)) {
//			vetReference[0] = 50.0 / dimPixel;
//			vetReference[1] = 60.0 / dimPixel;
//			vetReference[2] = 50.0 / dimPixel;
//			vetReference[3] = 215.0 / dimPixel;
//		}
//		return vetReference;

//	}

//	public void saveReferences(ImagePlus imp1) {
//		Line line1 = (Line) imp1.getRoi();
//		Prefs.set("prefer.p6rmnAx", "" + line1.x1);
//		Prefs.set("prefer.p6rmnAy", "" + line1.y1);
//		Prefs.set("prefer.p6rmnBx", "" + line1.x2);
//		Prefs.set("prefer.p6rmnBy", "" + line1.y2);
//	}

	// @SuppressWarnings("deprecation")
	public ResultsTable mainThickness(String[] path, String autoArgs, double[] vetRefPosition, boolean autoCalled,
			boolean step, boolean verbose, boolean test) {

		int nFrames = 0;
		double vetProfile[] = { 0, 0, 0, 0 }; // [xStart,yStart,xEnd,yEnd]
		double lato = 0;

		int len = path.length;
		String[] slicePos2 = new String[len];
		double[] fwhmSlice1 = new double[len];
		double[] peakPositionSlice1 = new double[len];
		double[] fwhmSlice2 = new double[len];
		double[] peakPositionSlice2 = new double[len];
		double[] fwhmCuneo3 = new double[len];
		double[] peakPositionCuneo3 = new double[len];
		double[] fwhmCuneo4 = new double[len];
		double[] peakPositionCuneo4 = new double[len];
		int[] vetAccettab = new int[len];
		double[] vetS1CorSlab = new double[len];
		double[] vetS2CorSlab = new double[len];
		double[] vetS1CorCuneo = new double[len];
		double[] vetS2CorCuneo = new double[len];
		double[] vetErrSpessSlab = new double[len];
		double[] vetErrSpessCuneo = new double[len];
		double[] vetAccurSpessSlab = new double[len];
		double[] vetAccurSpessCuneo = new double[len];
		ResultsTable rt = null;
//		ResultsTable rt11 = null;
		boolean salta = false;

//		double[] dsd0 = null;
		double[] totalFWHM1 = new double[10];
		double[] totalFWHM2 = new double[10];
		double[] totalFWHM3 = new double[10];
		double[] totalFWHM4 = new double[10];
		double[] dsd11 = new double[2];
		double[] dsd12 = new double[2];
		double[] dsd13 = new double[2];
		double[] dsd14 = new double[2];
		double fwhm1 = 0;
		double fwhm2 = 0;
		double fwhm3 = 0;
		double fwhm4 = 0;
		double fwhmGaus1 = 0;
		double fwhmGaus2 = 0;
		double fwhmGaus3 = 0;
		double fwhmGaus4 = 0;
		double fwhmSuper1 = 0;
		double fwhmSuper2 = 0;
		double fwhmSuper3 = 0;
		double fwhmSuper4 = 0;

		double scSlab1AAPMOrig = 0;
		double scSlab2AAPMOrig = 0;
		double scSlab1Orig = 0;
		double scSlab2Orig = 0;
		double scWedge1AAPMOrig = 0;
		double scWedge2AAPMOrig = 0;
		double scWedge1Orig = 0;
		double scWedge2Orig = 0;

		double sc21Gaus = 0;
		double sc22Gaus = 0;
		double sc31Gaus = 0;
		double sc32Gaus = 0;
		double sc41Super = 0;
		double sc42Super = 0;
		double sc51Super = 0;
		double sc52Super = 0;

		// =============================================================================
		// =============================================================================
		// MyLog.qui("forzo step a true");
		// step = true;
		// =============================================================================
		// =============================================================================

		ImagePlus impStack = stackBuilder(path, true);
		nFrames = path.length;

		impStack.setSliceWithoutUpdate(1);

		String stationName = ReadDicom.readDicomParameter(impStack, MyConst.DICOM_STATION_NAME);

		if (verbose) {
			UtilAyv.showImageMaximized(impStack);
		}
		float dimPixel = ReadDicom.readFloat(
				ReadDicom.readSubstring(ReadDicom.readDicomParameter(impStack, MyConst.DICOM_PIXEL_SPACING), 1));
		impStack.setRoi(new Line((int) vetRefPosition[0], (int) vetRefPosition[1], (int) vetRefPosition[2],
				(int) vetRefPosition[3]));
		impStack.updateAndDraw();
		int userSelection = 0;
		if (!test) {
			userSelection = msgSquare();
//			MyLog.waitHere("userSelection= " + userSelection);
		}
		if (userSelection == 1) {

			return null;

		}
		String thename = "";

		if (SPY) {
			IJ.log("------------ SELEZIONATI IN COMPILAZIONE --------------");
			IJ.log("SPY= " + SPY);
			IJ.log("P6_ORIGINAL= " + P6_ORIGINAL);
			IJ.log("P6_GAUSS= " + P6_GAUSS);
			IJ.log("P6_SUPERGAUSS= " + P6_SUPERGAUSS);
			IJ.log("---------------------------------------- --------------");

			spydir = p6rmn_COMMON.spyDirTree_COMMON(path, step);
			File f1 = new File(path[0]);
			thename = f1.getName();
		}

		//
		// legge la posizione finale del segmento dopo il posizionamento
		//
		Roi roi = impStack.getRoi();
		Line line1 = (Line) roi;
		vetRefPosition[0] = line1.x1;
		vetRefPosition[1] = line1.y1;
		vetRefPosition[2] = line1.x2;
		vetRefPosition[3] = line1.y2;
		lato = line1.getRawLength();

		if (!test) {
			p6rmn_COMMON.saveReferences_COMMON(impStack);
		}
		if (step && !SPY) {
			msgSquareCoordinates(vetRefPosition);
		}

		if (SPY) {
			p6rmn_COMMON.saveDebugImage_COMMON(impStack, spydir, thename, contaxx++, SPY);
		}

		//
		// elaborazione immagini dello stack
		//

		nFrames = impStack.getStackSize();

		double thick = ReadDicom.readDouble(ReadDicom.readDicomParameter(impStack, MyConst.DICOM_SLICE_THICKNESS));
		double spacing = ReadDicom
				.readDouble(ReadDicom.readDicomParameter(impStack, MyConst.DICOM_SPACING_BETWEEN_SLICES));

		for (int w1 = 0; w1 < nFrames; w1++) {

			ImagePlus imp3 = MyStackUtils.imageFromStack(impStack, w1 + 1);

			String pos2 = ReadDicom.readDicomParameter(imp3, MyConst.DICOM_IMAGE_POSITION);
			slicePos2[w1] = ReadDicom.readSubstring(pos2, 3);

			if (verbose || SPY) {
				UtilAyv.showImageMaximized(imp3);
			}
			if (nFrames > 1) {
				int userSelection3 = msgAccept();
				if (userSelection3 == 1) {
					salta = true;
				} else {
					salta = false;
				}

				vetAccettab[w1] = userSelection3;
			}

			if (!step)
			 {
				imp3.hide();
			// Phantom positioning: the phantom MUST have the slabs in high
			// position and the wedges in lower position
			//
			// First slab analysis
			//
			// refPosition [startX = 13, startY=65, endX = 147, endY=65,
			// radius=10]
			}

			// ====================================================================
			// ====================================================================
			// OLD
			// ====================================================================
			// ====================================================================

			if (salta) {
			} else {

				if (step && !SPY) {
					msgProfile();
				}
//				if (imp3.isVisible()) {
//					imp3.getWindow().toFront();
//				}
				boolean isSlab = true;
				boolean invertErf = false;
				boolean putLabelSx = false;
				int ra1 = 0;

				// ##################################################################################################
				// ##################################################################################################
				// ####-------------------------------ORIGINAL--------------------------------------------------#####
				// ##################################################################################################
				// ##################################################################################################
				//

				if (P6_ORIGINAL) {

//					if (SPY)
//						IJ.log("--------------  RISULTATI ORIGINALI ------------------");
//					if (SPY)
//						IJ.log("--------------  PRIMA SLAB ORIGINAL ------------------");
					ra1 = (int) (lato / 12.0);
					vetProfile[0] = lato / 13.0;
					vetProfile[1] = lato / 5.0;
					vetProfile[2] = lato - lato / 13.0;
					vetProfile[3] = lato / 5.0;
					// -----------
					isSlab = true;
					invertErf = false;
					putLabelSx = true;
					if (imp3.isVisible()) {
						imp3.getWindow().toFront();
					}
					// -----------
					dsd11 = analProf_ORIGINAL(imp3, vetRefPosition, vetProfile, ra1, isSlab, invertErf, step,
							putLabelSx, dimPixel);
					// -----------

					fwhmSlice1[w1] = dsd11[0];
					peakPositionSlice1[w1] = dsd11[1];
					fwhm1 = dsd11[0];
//					if (SPY) {
//						IJ.log("slab1 FWHM [pix]= " + fwhm1);
//						IJ.log("slab1 FWHM [mm]= " + fwhm1 * Math.sin(Math.toRadians(11.3)));
//					}
					if (step) {
						msgProfile();
					}
					// -----------
					//
					// Second slab analysis
					//
//					if (SPY)
//						IJ.log("--------------  SECONDA SLAB ORIGINAL ------------------");
					ra1 = (int) (lato / 12.0);
					vetProfile[0] = lato / 13.0;
					vetProfile[1] = lato * 2.0 / 5.0;
					vetProfile[2] = lato - lato / 13.0;
					vetProfile[3] = lato * 2.0 / 5.0;
					// -----------
					isSlab = true;
					invertErf = false;
					putLabelSx = true;
					if (step) {
						imp3.getWindow().toFront();
					}
					dsd12 = analProf_ORIGINAL(imp3, vetRefPosition, vetProfile, ra1, isSlab, invertErf, step,
							putLabelSx, dimPixel);
					fwhmSlice2[w1] = dsd12[0];
					peakPositionSlice2[w1] = dsd12[1];
					fwhm2 = dsd12[0];
//					if (SPY) {
//						IJ.log("slab2 FWHM [pix]= " + fwhm2);
//						IJ.log("slab2 FWHM [mm]= " + fwhm2 * Math.sin(Math.toRadians(11.3)));
//					}

					if (imp3.isVisible()) {
						imp3.getWindow().toFront();
					}
					double[] spessCorSlabOrig = spessStrato(fwhm1, fwhm2, thick, dimPixel);

					double[] spessCorSlabAAPMOrig = p6rmn_COMMON.spessStrato_AAPM100_COMMON(fwhm1, fwhm2,
							thick, dimPixel);

					vetS1CorSlab[w1] = spessCorSlabAAPMOrig[0];
					vetS2CorSlab[w1] = spessCorSlabAAPMOrig[1];
					vetErrSpessSlab[w1] = spessCorSlabAAPMOrig[2];
					vetAccurSpessSlab[w1] = spessCorSlabAAPMOrig[3];
					scSlab1AAPMOrig = spessCorSlabAAPMOrig[0];
					scSlab2AAPMOrig = spessCorSlabAAPMOrig[1];
					scSlab1Orig = spessCorSlabOrig[0];
					scSlab2Orig = spessCorSlabOrig[1];

					//
					// First wedge analysis
					//
					if (step)
					 {
						msgProfile();
					// refPosition [startX = 13, startY=98, endX = 147, endY=98,
					// radius=10]
//					if (SPY)
//						IJ.log("--------------  PRIMO CUNEO ORIGINAL ------------------");
					}

					ra1 = (int) (lato / 12.0);
					vetProfile[0] = lato / 13.0;
					vetProfile[1] = lato * 3.0 / 5.0;
					vetProfile[2] = lato - lato / 13.0;
					vetProfile[3] = lato * 3.0 / 5.0;

					isSlab = false;
					invertErf = true;
					putLabelSx = false;
					if (imp3.isVisible()) {
						imp3.getWindow().toFront();
					}

					dsd13 = analProf_ORIGINAL(imp3, vetRefPosition, vetProfile, ra1, isSlab, invertErf, step,
							putLabelSx, dimPixel);

					fwhmCuneo3[w1] = dsd13[0];
					peakPositionCuneo3[w1] = dsd13[1];

					fwhm3 = dsd13[0];
//					if (SPY) {
//						IJ.log("wedge1 FWHM [pix]= " + fwhm3);
//						IJ.log("wedge1 FWHM [mm]= " + fwhm3 * Math.sin(Math.toRadians(11.3)));
//					}

					if (imp3.isVisible()) {
						imp3.getWindow().toFront();
					}
					//
					// Second wedge analysis
					//
					if (step)
					 {
						msgProfile();
					// refPosition [startX = 13, startY=133, endX = 147, endY=133,
					// radius=10]
					}

//					if (SPY)
//						IJ.log("--------------  SECONDO CUNEO ORIGINAL ------------------");
					ra1 = (int) (lato / 12.0);
					vetProfile[0] = lato / 13.0;
					vetProfile[1] = lato * 4.0 / 5.0;
					vetProfile[2] = lato - lato / 13.0;
					vetProfile[3] = lato * 4.0 / 5.0;

					isSlab = false;
					invertErf = false;
					putLabelSx = true;
					if (step) {
						imp3.getWindow().toFront();
					}
					dsd14 = analProf_ORIGINAL(imp3, vetRefPosition, vetProfile, ra1, isSlab, invertErf, step,
							putLabelSx, dimPixel);
					fwhmCuneo4[w1] = dsd14[0];
					peakPositionCuneo4[w1] = dsd14[1];

					fwhm4 = dsd14[0];
//					if (SPY) {
//						IJ.log("wedge2 FWHM [pix]= " + fwhm4);
//						IJ.log("wedge2 FWHM [mm]= " + fwhm4 * Math.sin(Math.toRadians(11.3)));
//					}

					if (imp3.isVisible()) {
						imp3.getWindow().toFront();
					}
//					if (SPY)
//						IJ.log("--------------------  ORIGINAL ------------------------");
					double[] spessCorCuneoOrig = spessStrato(fwhm3, fwhm4, thick, dimPixel);
					double[] spessCorCuneoAAPMOrig = p6rmn_COMMON.spessStrato_AAPM100_COMMON(fwhm3, fwhm4,
							thick, dimPixel);

					vetS1CorCuneo[w1] = spessCorCuneoAAPMOrig[0];
					vetS2CorCuneo[w1] = spessCorCuneoAAPMOrig[1];
					vetErrSpessCuneo[w1] = spessCorCuneoAAPMOrig[2];
					vetAccurSpessCuneo[w1] = spessCorCuneoAAPMOrig[3];
					scWedge1AAPMOrig = spessCorCuneoAAPMOrig[0];
					scWedge2AAPMOrig = spessCorCuneoAAPMOrig[1];
					scWedge1Orig = spessCorSlabOrig[0];
					scWedge2Orig = spessCorSlabOrig[1];

				}

				//
				// ##################################################################################################
				// ##################################################################################################
				// ####--------------------------------FITTER---------------------------------------------------#####
				// ##################################################################################################
				// ##################################################################################################
				//

				if (P6_GAUSS || P6_SUPERGAUSS) {
//					if (SPY)
//						IJ.log("--------------  PRIMA SLAB FITTER ------------------");
					if (step && !SPY) {
						msgProfile();
					}
					isSlab = true;
					invertErf = false;
					putLabelSx = true;
					if (step) {
						imp3.getWindow().toFront();
					}
					// ----
					ra1 = (int) (lato / 12.0);
					vetProfile[0] = lato / 13.0;
					vetProfile[1] = lato / 5.0;
					vetProfile[2] = lato - lato / 13.0;
					vetProfile[3] = lato / 5.0;
					// ----
//					IJ.log("ESEGUO analProf_FITTER su PRIMA SLAB");
					totalFWHM1 = analProf_FITTER(imp3, vetRefPosition, vetProfile, ra1, isSlab, invertErf, step,
							putLabelSx, dimPixel);
					// ----

					if (P6_GAUSS) {
						fwhmSlice1[w1] = totalFWHM1[0];
						fwhmGaus1 = totalFWHM1[0];
						peakPositionSlice1[w1] = totalFWHM1[1];
					}

					if (P6_SUPERGAUSS) {
						fwhmSlice1[w1] = totalFWHM1[5];
						fwhmSuper1 = totalFWHM1[5];
						peakPositionSlice1[w1] = totalFWHM1[7];
					}

					// ----
//					if (SPY)
//						IJ.log("--------------  SECONDA SLAB FITTER ------------------");
					isSlab = true;
					invertErf = false;
					putLabelSx = true;
					if (imp3.isVisible()) {
						imp3.getWindow().toFront();
					}
					// ----
					ra1 = (int) (lato / 12.0);
					vetProfile[0] = lato / 13.0;
					vetProfile[1] = lato * 2.0 / 5.0;
					vetProfile[2] = lato - lato / 13.0;
					vetProfile[3] = lato * 2.0 / 5.0;
					// ----
//					IJ.log("ESEGUO analProf_FITTER su SECONDA SLAB");
					totalFWHM2 = analProf_FITTER(imp3, vetRefPosition, vetProfile, ra1, isSlab, invertErf, step,
							putLabelSx, dimPixel);
					if (P6_GAUSS) {
						fwhmSlice2[w1] = totalFWHM2[0];
						fwhmGaus2 = totalFWHM2[0];
						peakPositionSlice2[w1] = totalFWHM2[1];
//						if (SPY)
//							IJ.log("--------------------  GAUSSIAN ------------------------");
						double[] spessCor3Gauss = spessStrato(fwhmGaus1, fwhmGaus2, thick, dimPixel);
						double[] spessCor1Gauss = p6rmn_COMMON.spessStrato_AAPM100_COMMON(fwhmGaus1, fwhmGaus2,
								thick, dimPixel);

						sc21Gaus = spessCor1Gauss[0];
						sc22Gaus = spessCor1Gauss[1];
						if (SPY) {
							IJ.log("slabGaussiana corretta = " + sc21Gaus);
						}
					}
					if (P6_SUPERGAUSS) {
						fwhmSlice2[w1] = totalFWHM2[5];
						fwhmSuper2 = totalFWHM2[5];

						peakPositionSlice2[w1] = totalFWHM2[7];
//						if (SPY)
//							IJ.log("------------------ SUPERGAUSSIAN ----------------------");
						double[] spessCor3Super = spessStrato(fwhmSuper1, fwhmSuper2, thick, dimPixel);
						double[] spessCor1Super = p6rmn_COMMON.spessStrato_AAPM100_COMMON(fwhmSuper1, fwhmSuper2,
								thick, dimPixel);
						sc41Super = spessCor1Super[0];
						sc42Super = spessCor1Super[1];
//						if (SPY)
//							IJ.log("slabSuper corretta = " + sc41Super);
					}
					// ----
//					if (SPY)
//						IJ.log("--------------  PRIMO CUNEO FITTER ------------------");
					if (step && !SPY) {
						msgProfile();
					}
					isSlab = false;
					invertErf = true;
					putLabelSx = false;
					if (imp3.isVisible()) {
						imp3.getWindow().toFront();
					}
					// ----
					ra1 = (int) (lato / 12.0);
					vetProfile[0] = lato / 13.0;
					vetProfile[1] = lato * 3.0 / 5.0;
					vetProfile[2] = lato - lato / 13.0;
					vetProfile[3] = lato * 3.0 / 5.0;
					// ----
//					IJ.log("ESEGUO analProf_FITTER su PRIMO CUNEO");
					totalFWHM3 = analProf_FITTER(imp3, vetRefPosition, vetProfile, ra1, isSlab, invertErf, step,
							putLabelSx, dimPixel);
					if (P6_GAUSS) {
						fwhmCuneo3[w1] = totalFWHM3[0];
						fwhmGaus3 = totalFWHM3[0];
						peakPositionCuneo3[w1] = totalFWHM3[1];
					}

					if (P6_SUPERGAUSS) {
						fwhmCuneo3[w1] = totalFWHM3[5];
						fwhmSuper3 = totalFWHM3[5];
						peakPositionCuneo3[w1] = totalFWHM3[7];
					}

					// ----
//					if (SPY)
//						IJ.log("--------------  SECONDO CUNEO FITTER ------------------");
					isSlab = false;
					invertErf = true;
					putLabelSx = false;
					if (imp3.isVisible()) {
						imp3.getWindow().toFront();
					}
					ra1 = (int) (lato / 12.0);
					vetProfile[0] = lato / 13.0;
					vetProfile[1] = lato * 4.0 / 5.0;
					vetProfile[2] = lato - lato / 13.0;
					vetProfile[3] = lato * 4.0 / 5.0;
					// ----
//					IJ.log("ESEGUO analProf_FITTER su SECONDO CUNEO");
					totalFWHM4 = analProf_FITTER(imp3, vetRefPosition, vetProfile, ra1, isSlab, invertErf, step,
							putLabelSx, dimPixel);
				}
				// ----
				if (P6_GAUSS) {
					fwhmCuneo4[w1] = totalFWHM4[0];
					fwhmGaus4 = totalFWHM4[0];

					peakPositionCuneo4[w1] = totalFWHM4[1];
//					if (SPY)
//						IJ.log("--------------------  GAUSSIAN ------------------------");
					double[] spessCor4Gauss = spessStrato(fwhmGaus3, fwhmGaus4, thick, dimPixel);
					double[] spessCor2Gauss = p6rmn_COMMON.spessStrato_AAPM100_COMMON(fwhmGaus3, fwhmGaus4,
							thick, dimPixel);
					sc31Gaus = spessCor2Gauss[0];
					sc32Gaus = spessCor2Gauss[1];
//					if (SPY)
//						IJ.log("wedgeGaus corretto = " + sc31Gaus);
				}
				// ----
				if (P6_SUPERGAUSS) {
					fwhmCuneo4[w1] = totalFWHM4[5];
					fwhmSuper4 = totalFWHM4[5];
					peakPositionCuneo4[w1] = totalFWHM4[7];
//					if (SPY)
//						IJ.log("------------------ SUPERGAUSSIAN ----------------------");
					double[] spessCor4Super = spessStrato(fwhmSuper3, fwhmSuper4, thick, dimPixel);
					double[] spessCor2Super = p6rmn_COMMON.spessStrato_AAPM100_COMMON(fwhmSuper3, fwhmSuper4,
							thick, dimPixel);
					sc51Super = spessCor2Super[0];
					sc52Super = spessCor2Super[1];
//					if (SPY)
//						IJ.log("wedgeSuper corretta = " + sc51Super);
				}
			}
		}

		// ho disposto le cose in modo che se P6_SUPERGAUSS, P6GAUSS e P6ORIGINAL sono
		// selezionati contemporaneamente, vinca sempre P6SUPERGAUSS

		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
		LocalDateTime now = LocalDateTime.now();
		String ora2 = dtf.format(now);

		// ###################### RIEPILOGO DIFFERENZE IN MILLIMETRI ORIGINALE
		// ##########################
		String or1 = addPlus(IJ.d2s((fwhm1 * dimPixel * Math.sin(Math.toRadians(11.3))) - thick, 4));
		String or2 = addPlus(IJ.d2s((fwhm2 * dimPixel * Math.sin(Math.toRadians(11.3))) - thick, 4));
		String or3 = addPlus(IJ.d2s((fwhm3 * dimPixel * Math.sin(Math.toRadians(11.3))) - thick, 4));
		String or4 = addPlus(IJ.d2s((fwhm4 * dimPixel * Math.sin(Math.toRadians(11.3))) - thick, 4));
		// ###################### RIEPILOGO DIFFERENZE IN MILLIMETRI GAUSSIANO
		// ##########################
		String gg1 = addPlus(IJ.d2s(fwhmGaus1 * dimPixel * Math.sin(Math.toRadians(11.3)) - thick, 4));
		String gg2 = addPlus(IJ.d2s(fwhmGaus2 * dimPixel * Math.sin(Math.toRadians(11.3)) - thick, 4));
		String gg3 = addPlus(IJ.d2s(fwhmGaus3 * dimPixel * Math.sin(Math.toRadians(11.3)) - thick, 4));
		String gg4 = addPlus(IJ.d2s(fwhmGaus4 * dimPixel * Math.sin(Math.toRadians(11.3)) - thick, 4));
		// ###################### RIEPILOGO DIFFERENZE IN MILLIMETRI SUPERGAUSSIANO
		// ##########################
		String sg1 = addPlus(IJ.d2s(fwhmSuper1 * dimPixel * Math.sin(Math.toRadians(11.3)) - thick, 4));
		String sg2 = addPlus(IJ.d2s(fwhmSuper2 * dimPixel * Math.sin(Math.toRadians(11.3)) - thick, 4));
		String sg3 = addPlus(IJ.d2s(fwhmSuper3 * dimPixel * Math.sin(Math.toRadians(11.3)) - thick, 4));
		String sg4 = addPlus(IJ.d2s(fwhmSuper4 * dimPixel * Math.sin(Math.toRadians(11.3)) - thick, 4));

		// ###################### ORIG_CORREZIONE TILT
		// ##############################################
		String cor01 = addPlus(IJ.d2s((scSlab1AAPMOrig * dimPixel - thick), 4));
		String cor03 = addPlus(IJ.d2s((scSlab1Orig * dimPixel - thick), 4));
//		String cor02 = addPlus(IJ.d2s((sc02Orig * dimPixel - thick), 4));
		String cor11 = addPlus(IJ.d2s((scWedge1AAPMOrig * dimPixel - thick), 4));
		String cor13 = addPlus(IJ.d2s((scWedge1Orig * dimPixel - thick), 4));
//		String cor12 = addPlus(IJ.d2s((sc12Orig * dimPixel - thick), 4));
		// ###################### GAUSS_CORREZIONE TILT
		// ##############################################
		String cor21 = addPlus(IJ.d2s((sc21Gaus * dimPixel - thick), 4));
//		String cor22 = addPlus(IJ.d2s((sc22Gaus * dimPixel - thick), 4));
		String cor31 = addPlus(IJ.d2s((sc31Gaus * dimPixel - thick), 4));
//		String cor32 = addPlus(IJ.d2s((sc32Gaus * dimPixel - thick), 4));
		// ###################### SUPERGAUSS_CORREZIONE TILT
		// ##############################################
		String cor41 = addPlus(IJ.d2s((sc41Super * dimPixel - thick), 4));
//		String cor42 = addPlus(IJ.d2s((sc42Super * dimPixel - thick), 4));
		String cor51 = addPlus(IJ.d2s((sc51Super * dimPixel - thick), 4));
//		String cor52 = addPlus(IJ.d2s((sc52Super * dimPixel - thick), 4));

		String mm01 = addPlus(IJ.d2s((scSlab1AAPMOrig * dimPixel), 4));
		String mm03 = addPlus(IJ.d2s((scSlab1Orig * dimPixel), 4));
		String mm11 = addPlus(IJ.d2s((scWedge1AAPMOrig * dimPixel), 4));
		String mm13 = addPlus(IJ.d2s((scSlab2Orig * dimPixel), 4));

		String mm21 = addPlus(IJ.d2s((sc21Gaus * dimPixel), 4));
		String mm31 = addPlus(IJ.d2s((sc31Gaus * dimPixel), 4));
		String mm41 = addPlus(IJ.d2s((sc41Super * dimPixel), 4));
		String mm51 = addPlus(IJ.d2s((sc51Super * dimPixel), 4));

		// ###################### RIEPILOGO RISULTATI IN MILLIMETRI ORIGINALE
		// ##########################
		String res1 = IJ.d2s((fwhm1 * dimPixel * Math.sin(Math.toRadians(11.3))), 4);
		String res2 = IJ.d2s((fwhm2 * dimPixel * Math.sin(Math.toRadians(11.3))), 4);
		String res3 = IJ.d2s((fwhm3 * dimPixel * Math.sin(Math.toRadians(11.3))), 4);
		String res4 = IJ.d2s((fwhm4 * dimPixel * Math.sin(Math.toRadians(11.3))), 4);

		// ###################### RIEPILOGO RISULTATI IN MILLIMETRI FIT_GAUSS
		// ##########################
		String fgaus1 = IJ.d2s((fwhmGaus1 * dimPixel * Math.sin(Math.toRadians(11.3))), 4);
		String fgaus2 = IJ.d2s((fwhmGaus2 * dimPixel * Math.sin(Math.toRadians(11.3))), 4);
		String fgaus3 = IJ.d2s((fwhmGaus3 * dimPixel * Math.sin(Math.toRadians(11.3))), 4);
		String fgaus4 = IJ.d2s((fwhmGaus4 * dimPixel * Math.sin(Math.toRadians(11.3))), 4);

		// ###################### RIEPILOGO RISULTATI IN MILLIMETRI FIT_GAUSS R^2
		// ##########################
		String rg1 = IJ.d2s(totalFWHM1[4], 4);
		String rg2 = IJ.d2s(totalFWHM2[4], 4);
		String rg3 = IJ.d2s(totalFWHM3[4], 4);
		String rg4 = IJ.d2s(totalFWHM4[4], 4);

		// ###################### RIEPILOGO RISULTATI IN MILLIMETRI FIT_SUPERGAUSS
		// ##########################
		String fsuper01 = IJ.d2s((fwhmSuper1 * dimPixel * Math.sin(Math.toRadians(11.3))), 4);
		String fsuper02 = IJ.d2s((fwhmSuper2 * dimPixel * Math.sin(Math.toRadians(11.3))), 4);
		String fsuper03 = IJ.d2s((fwhmSuper3 * dimPixel * Math.sin(Math.toRadians(11.3))), 4);
		String fsuper04 = IJ.d2s((fwhmSuper4 * dimPixel * Math.sin(Math.toRadians(11.3))), 4);

		// ###################### RIEPILOGO RISULTATI IN MILLIMETRI FIT_SUPERGAUSS R^2
		// ##########################
		String rs1 = IJ.d2s(totalFWHM1[9], 4);
		String rs2 = IJ.d2s(totalFWHM2[9], 4);
		String rs3 = IJ.d2s(totalFWHM3[9], 4);
		String rs4 = IJ.d2s(totalFWHM4[9], 4);

//		String pold1 = IJ.d2s((dsd11[0]), 4);
//		String pold2 = IJ.d2s((dsd12[0]), 4);
//		String pold3 = IJ.d2s((dsd13[0]), 4);
//		String pold4 = IJ.d2s((dsd14[0]), 4);

//		String pgaus1 = IJ.d2s((totalFWHM1[0]), 4);
//		String pgaus2 = IJ.d2s((totalFWHM2[0]), 4);
//		String pgaus3 = IJ.d2s((totalFWHM3[0]), 4);
//		String pgaus4 = IJ.d2s((totalFWHM4[0]), 4);

//		String psuper01 = IJ.d2s((totalFWHM1[5]), 4);
//		String psuper02 = IJ.d2s((totalFWHM2[5]), 4);
//		String psuper03 = IJ.d2s((totalFWHM3[5]), 4);
//		String psuper04 = IJ.d2s((totalFWHM4[5]), 4);

		if (true) {

			IJ.log("==================================================================");
			IJ.log("======================= p6rmn_FITTER ===========================");
			IJ.log("==================================================================");
			IJ.log("\n");

			IJ.log(MyLog.qui());
//			IJ.log("##########################################################################################################################################");
//			IJ.log("##########################################################################################################################################");
//			IJ.log("######################                                    ################################################################################");
//			IJ.log("######################  RIEPILOGO RISULTATI IN PIXELS     ################################################################################");
//			IJ.log("######################                                    ################################################################################");
//			IJ.log("##########################################################################################################################################");
//			IJ.log("##########################################################################################################################################");
//			IJ.log("SULLO SPESSORE TEORICO DI " + thick
//					+ " mm I DIVERSI ALGORITMI DI CALCOLO DANNO I SEGUENTI RISULTATI ESPRESSI IN pixels");
//			IJ.log("                                   SLAB_1    R^2           SLAB_2    R^2           CUNEO_1    R^2          CUNEO_2    R^2");
//			IJ.log("VECCHIO CALCOLO                    " + pold1 + "                  " + pold2 + "                  "
//					+ pold3 + "                  " + pold4);
//			IJ.log("FIT GAUSSIANA                      " + pgaus1 + "    " + rg1 + "        " + pgaus2 + "    " + rg2
//					+ "        " + pgaus3 + "     " + rg3 + "       " + pgaus4 + "     " + rg4);
//			IJ.log("FIT SUPER-GAUSSIANA                " + psuper01 + "    " + rs1 + "        " + psuper02 + "    "
//					+ rs2 + "        " + psuper03 + "     " + rs3 + "       " + psuper04 + "     " + rs4);
			IJ.log("##########################################################################################################################################");
			IJ.log("##########################################################################################################################################");
			IJ.log("######################                                    ################################################################################");
			IJ.log("###################### RIEPILOGO RISULTATI IN MILLIMETRI  ################################################################################");
			IJ.log("######################                                    ################################################################################");
			IJ.log("##########################################################################################################################################");
			IJ.log("##########################################################################################################################################");
			IJ.log("SULLO SPESSORE TEORICO DI " + thick
					+ " mm I DIVERSI ALGORITMI DI CALCOLO DANNO I SEGUENTI RISULTATI ESPRESSI IN mm");
			IJ.log("                                   SLAB_1    R^2           SLAB_2    R^2           CUNEO_1    R^2          CUNEO_2    R^2");
			IJ.log("VECCHIO CALCOLO                    " + res1 + "                  " + res2 + "                  "
					+ res3 + "                  " + res4);
			IJ.log("FIT GAUSSIANA                      " + fgaus1 + "    " + rg1 + "        " + fgaus2 + "    " + rg2
					+ "        " + fgaus3 + "     " + rg3 + "       " + fgaus4 + "     " + rg4);
			IJ.log("FIT SUPER-GAUSSIANA                " + fsuper01 + "    " + rs1 + "        " + fsuper02 + "    "
					+ rs2 + "        " + fsuper03 + "     " + rs3 + "       " + fsuper04 + "     " + rs4);
			IJ.log("##########################################################################################################################################");
			IJ.log("##########################################################################################################################################");
			IJ.log("######################                                    ################################################################################");
			IJ.log("######################      RIEPILOGO DIFFERENZE          ################################################################################");
			IJ.log("######################                                    ################################################################################");
			IJ.log("##########################################################################################################################################");
			IJ.log("##########################################################################################################################################");
			IJ.log("##########################################################################################################################################");
			IJ.log("SULLO SPESSORE TEORICO DI " + thick
					+ " mm I DIVERSI ALGORITMI DI CALCOLO DANNO LE SEGUENTI DIFFERENZE ESPRESSE IN mm");
			IJ.log("                            	   SLAB_1                  SLAB_2                  CUNEO_1                 CUNEO_2");
			IJ.log("VECCHIO CALCOLO                    " + or1 + "                 " + or2 + "                 " + or3
					+ "                 " + or4 + "");
			IJ.log("FIT GAUSSIANA                      " + gg1 + "                 " + gg2 + "                 " + gg3
					+ "                 " + gg4 + "");
			IJ.log("FIT SUPER-GAUSSIANA                " + sg1 + "                 " + sg2 + "                 " + sg3
					+ "                 " + sg4 + "");
			IJ.log("##########################################################################################################################################");
			IJ.log("##########################################################################################################################################");
			IJ.log("######################                                    ################################################################################");
			IJ.log("######################  DOPO_VECCHIA CORREZIONE_TILT      ################################################################################");
			IJ.log("######################                                    ################################################################################");
			IJ.log("##########################################################################################################################################");
			IJ.log("##########################################################################################################################################");
			IJ.log("SULLO SPESSORE TEORICO DI " + thick
					+ " mm I DIVERSI ALGORITMI DI CALCOLO DANNO I SEGUENTI RISULTATI ESPRESSI IN mm");
			IJ.log("                            	   SLAB_1+2    DIFF                                CUNEO_1+2   DIFF");
			IJ.log("VECCHIO CALCOLO                    " + mm03 + "    " + cor03 + "                              "
					+ mm13 + "    " + cor13);
			IJ.log("FIT GAUSSIANA                      " + mm21 + "    " + cor21 + "                              "
					+ mm31 + "    " + cor31);
			IJ.log("FIT SUPER-GAUSSIANA                " + mm41 + "    " + cor41 + "                              "
					+ mm51 + "    " + cor51);
			IJ.log("##########################################################################################################################################");
			IJ.log("##########################################################################################################################################");
			IJ.log("######################                                    ################################################################################");
			IJ.log("######################  DOPO_CORREZIONE_TILT CON AAPM100  ################################################################################");
			IJ.log("######################                                    ################################################################################");
			IJ.log("##########################################################################################################################################");
			IJ.log("##########################################################################################################################################");
			IJ.log("SULLO SPESSORE TEORICO DI " + thick
					+ " mm I DIVERSI ALGORITMI DI CALCOLO DANNO I SEGUENTI RISULTATI ESPRESSI IN mm");
			IJ.log("                            	   SLAB_1+2    DIFF                                CUNEO_1+2   DIFF");
			IJ.log("VECCHIO CALCOLO                    " + mm01 + "    " + cor01 + "                              "
					+ mm11 + "    " + cor11);
			IJ.log("FIT GAUSSIANA                      " + mm21 + "    " + cor21 + "                              "
					+ mm31 + "    " + cor31);
			IJ.log("FIT SUPER-GAUSSIANA                " + mm41 + "    " + cor41 + "                              "
					+ mm51 + "    " + cor51);
			IJ.log("##########################################################################################################################################");
			IJ.log("##########################################################################################################################################");
			IJ.log("Test del " + ora2);
			IJ.log("macchina " + stationName);
			// ##################################################################################################
			// ##################################################################################################
			//
//			IJ.log("---------- RISULTATI ORIGINALE CON CONTEGGIO PIXEL ----------");
//			IJ.log(" FWHMconteggio [pixel]= " + dsd11[0] + " FWHMconteggio [mm]= "
//					+ dsd11[0] * dimPixel * Math.sin(Math.toRadians(11.3)));
//			IJ.log(" FWHMconteggio [pixel]= " + dsd12[0] + " FWHMconteggio [mm]= "
//					+ dsd12[0] * dimPixel * Math.sin(Math.toRadians(11.3)));
//			IJ.log(" FWHMconteggio [pixel]= " + dsd13[0] + " FWHMconteggio [mm]= "
//					+ dsd13[0] * dimPixel * Math.sin(Math.toRadians(11.3)));
//			IJ.log(" FWHMconteggio [pixel]= " + dsd14[0] + " FWHMconteggio [mm]= "
//					+ dsd14[0] * dimPixel * Math.sin(Math.toRadians(11.3)));
//			IJ.log("---------- RISULTATI GAUSSIANA CON CONTEGGIO PIXEL ----------");
//			IJ.log("slab1 R^2= " + totalFWHM1[4] + " FWHMconteggio [pixel]= " + totalFWHM1[0] + " FWHMconteggio [mm]= "
//					+ totalFWHM1[0] * dimPixel * Math.sin(Math.toRadians(11.3)) + " FWHMformula[pixel]= "
//					+ totalFWHM1[1] + " FWHMformula [mm]= "
//					+ totalFWHM1[1] * dimPixel * Math.sin(Math.toRadians(11.3)));
//			IJ.log("slab2 R^2= " + totalFWHM2[4] + " FWHMconteggio [pixel]= " + totalFWHM2[0] + " FWHMconteggio [mm]= "
//					+ totalFWHM2[0] * dimPixel * Math.sin(Math.toRadians(11.3)) + " FWHMformula[pixel]= "
//					+ totalFWHM2[1] + " FWHMformula [mm]= "
//					+ totalFWHM2[1] * dimPixel * Math.sin(Math.toRadians(11.3)));
//			IJ.log("wedge1 R^2= " + totalFWHM3[4] + " FWHMconteggio [pixel]= " + totalFWHM3[0] + " FWHMconteggio [mm]= "
//					+ totalFWHM3[0] * dimPixel * Math.sin(Math.toRadians(11.3)) + " FWHMformula[pixel]= "
//					+ totalFWHM3[1] + " FWHMformula [mm]= "
//					+ totalFWHM3[1] * dimPixel * Math.sin(Math.toRadians(11.3)));
//			IJ.log("wedge2 R^2= " + totalFWHM4[4] + " FWHMconteggio [pixel]= " + totalFWHM4[0] + " FWHMconteggio [mm]= "
//					+ totalFWHM4[0] * dimPixel * Math.sin(Math.toRadians(11.3)) + " FWHMformula[pixel]= "
//					+ totalFWHM4[1] + " FWHMformula [mm]= "
//					+ totalFWHM4[1] * dimPixel * Math.sin(Math.toRadians(11.3)));
//			IJ.log("------- RISULTATI SUPER-GAUSSIANA CON CONTEGGIO PIXEL --------");
//			IJ.log("slab1 R^2= " + totalFWHM1[9] + " FWHMconteggio [pixel]= " + totalFWHM1[5] + " FWHMconteggio [mm]= "
//					+ totalFWHM1[5] * dimPixel * Math.sin(Math.toRadians(11.3)) + " FWHMformula[pixel]= "
//					+ totalFWHM1[6] + " FWHMformula [mm]= "
//					+ totalFWHM1[6] * dimPixel * Math.sin(Math.toRadians(11.3)));
//			IJ.log("slab2 R^2= " + totalFWHM2[9] + " FWHMconteggio [pixel]= " + totalFWHM2[5] + " FWHMconteggio [mm]= "
//					+ totalFWHM2[5] * dimPixel * Math.sin(Math.toRadians(11.3)) + " FWHMformula[pixel]= "
//					+ totalFWHM2[6] + " FWHMformula [mm]= "
//					+ totalFWHM2[6] * dimPixel * Math.sin(Math.toRadians(11.3)));
//			IJ.log("wedge1 R^2= " + totalFWHM3[9] + " FWHMconteggio [pixel]= " + totalFWHM3[5] + " FWHMconteggio [mm]= "
//					+ totalFWHM3[5] * dimPixel * Math.sin(Math.toRadians(11.3)) + " FWHMformula[pixel]= "
//					+ totalFWHM3[6] + " FWHMformula [mm]= "
//					+ totalFWHM3[6] * dimPixel * Math.sin(Math.toRadians(11.3)));
//			IJ.log("wedge2 R^2= " + totalFWHM4[9] + " FWHMconteggio [pixel]= " + totalFWHM4[5] + " FWHMconteggio [mm]= "
//					+ totalFWHM4[5] * dimPixel * Math.sin(Math.toRadians(11.3)) + " FWHMformula[pixel]= "
//					+ totalFWHM4[6] + " FWHMformula [mm]= "
//					+ totalFWHM4[6] * dimPixel * Math.sin(Math.toRadians(11.3)));
			p6rmn_COMMON.saveLog_COMMON(spydir, "Log2Results.txt", SPY);
		}

		//
		// Salvataggio dei risultati nella results table
		//

		// String[][] tabCodici = new InputOutput().readFile1(MyConst.CODE_FILE,
		// MyConst.TOKENS4);

		// String[][] tabCodici = TableCode.loadMultipleTable(MyConst.CODE_GROUP);
		TableCode tc1 = new TableCode();
		String[][] tabCodici = tc1.loadMultipleTable("codici", ".csv");

//		MyLog.waitHere("path1= "+path[0]);
		String[] info1 = ReportStandardInfo.getSimpleStandardInfo(path[0], impStack, tabCodici, VERSION, autoCalled);
		rt = ReportStandardInfo.putSimpleStandardInfoRT(info1);
		rt.showRowNumbers(true);

		String t1 = "TESTO";
		String s2 = "SLICE";
		String s3 = "seg_ax";
		String s4 = "seg_ay";
		String s5 = "seg_bx";
		String s6 = "seg_by";

		// rt.setHeading(++col, t1);
		// for (int j1 = 0; j1 < nFrames; j1++)
		// rt.setHeading(++col, "SLICE" + j1);
		// rt.setHeading(++col, "seg_ax");
		// rt.setHeading(++col, "seg_ay");
		// rt.setHeading(++col, "seg_bx");
		// rt.setHeading(++col, "seg_by");

		rt.addValue(t1, " slicePos");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, UtilAyv.convertToDouble(slicePos2[j1]));
		}
		rt.addValue(s3, vetRefPosition[0]);
		rt.addValue(s4, vetRefPosition[1]);
		rt.addValue(s5, vetRefPosition[2]);
		rt.addValue(s6, vetRefPosition[3]);

		rt.incrementCounter();
		rt.addValue(t1, "fwhm_slab1");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, fwhmSlice1[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "peak_slab1");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, peakPositionSlice1[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "fwhm_slab2");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, fwhmSlice2[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "peak_slab2");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, peakPositionSlice2[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "fwhm_cuneo3");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, fwhmCuneo3[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "peak_cuneo3");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, peakPositionCuneo3[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "fwhm_cuneo4");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, fwhmCuneo4[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "peak_cuneo4");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, peakPositionCuneo4[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "S1CorSlab");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, vetS1CorSlab[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "S2CorSlab");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, vetS2CorSlab[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "ErrSperSlab");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, vetErrSpessSlab[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "AccurSpesSlab");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, vetAccurSpessSlab[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "S1CorCuneo");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, vetS1CorCuneo[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "S2CorCuneo");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, vetS2CorCuneo[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "ErrSperCuneo");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, vetErrSpessCuneo[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "AccurSpesCuneo");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, vetAccurSpessCuneo[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "Accettab");
		for (int j1 = 0; j1 < nFrames; j1++) {
			rt.addValue(s2 + j1, vetAccettab[j1]);
		}

		rt.incrementCounter();
		rt.addValue(t1, "DimPix");
		rt.addValue(s2 + 0, dimPixel);

		rt.incrementCounter();
		rt.addValue(t1, "Thick");
		rt.addValue(s2 + 0, thick);

		rt.incrementCounter();
		rt.addValue(t1, "Spacing");
		rt.addValue(s2 + 0, spacing);

		return rt;
	}

//	public void saveDebugImage_CANCELLABILE(ImagePlus imp1, String first, String second, String name) {
//		ImagePlus impS3 = imp1.flatten();
//		String newName = first + second + "\\" + name + "@" + IJ.pad(contaxx++, 3) + ".jpg";
//		FileSaver fs = new FileSaver(impS3);
//		// FileSaver.setJpegQuality(100);
//		if (SPY)
//			fs.saveAsJpeg(newName);
//	}

	/**
	 * Costruisce uno stack a partire dal path delle immagini e lo riordina secondo
	 * la posizione delle fette.
	 *
	 * @param path vettore contenente il path delle immagini
	 * @return ImagePlus contenente lo stack generato
	 */
	public ImagePlus stackBuilder(String[] path, boolean verbose) {

		if ((path == null) || (path.length == 0)) {
			if (verbose) {
				IJ.log("stackBuilder path==null or 0 length.");
			}
			return null;
		}
		ImagePlus imp0 = UtilAyv.openImageNoDisplay(path[0], true);
		int rows = imp0.getHeight();
		int columns = imp0.getWidth();
		ImageStack newStack = new ImageStack(rows, columns);
		String[] slicePos2 = new String[path.length];

		for (int w1 = 0; w1 < path.length; w1++) {
			imp0 = UtilAyv.openImageNoDisplay(path[w1], true);
			String dicomPosition = ReadDicom.readDicomParameter(imp0, MyConst.DICOM_IMAGE_POSITION);
			slicePos2[w1] = ReadDicom.readSubstring(dicomPosition, 3);
		}

		String[] pathSortato = bubbleSortPath(path, slicePos2);

		for (int w1 = 0; w1 < pathSortato.length; w1++) {
			ImagePlus imp1 = UtilAyv.openImageNoDisplay(path[w1], true);
			ImageProcessor ip1 = imp1.getProcessor();
			if (w1 == 0) {
				newStack.update(ip1);
			}
			String sliceInfo1 = imp1.getTitle();
			String sliceInfo2 = (String) imp1.getProperty("Info");
			// aggiungo i dati header alle singole immagini dello stack
			if (sliceInfo2 != null) {
				sliceInfo1 += "\n" + sliceInfo2;
			}
			newStack.addSlice(sliceInfo1, ip1);
		}
		// 180419 aggiunto eventuale codice del nome immagine anche allo stack
		File f = new File(path[0]);
		String nome1 = f.getName();
		String nome2 = nome1.substring(0, 5);
		// MyLog.waitHere("nome2= "+nome2);
		ImagePlus newImpStack = new ImagePlus(nome2 + "_newSTACK", newStack);
		if (pathSortato.length == 1) {
			String sliceInfo3 = imp0.getTitle();
			sliceInfo3 += "\n" + (String) imp0.getProperty("Info");
			newImpStack.setProperty("Info", sliceInfo3);
		}
		newImpStack.getProcessor().resetMinAndMax();
		return newImpStack;
	}

	/**
	 *
	 * @param path
	 * @param slicePosition
	 * @return
	 */
	public String[] bubbleSortPath(String[] path, String[] slicePosition) {

		if ((path == null) || (slicePosition == null) || !(path.length == slicePosition.length)) {
			return null;
		}
		if (path.length < 2) {
			return path;
		}
		//
		// bubblesort
		//
		String[] sortedPath = new String[path.length];
		sortedPath = path;

		for (int i1 = 0; i1 < path.length; i1++) {
			for (int i2 = 0; i2 < path.length - 1 - i1; i2++) {
				double position1 = ReadDicom.readDouble(slicePosition[i2]);
				double position2 = ReadDicom.readDouble(slicePosition[i2 + 1]);
				if (position1 > position2) {
					String positionSwap = slicePosition[i2];
					slicePosition[i2] = slicePosition[i2 + 1];
					slicePosition[i2 + 1] = positionSwap;
					String pathSwap = sortedPath[i2];
					sortedPath[i2] = sortedPath[i2 + 1];
					sortedPath[i2 + 1] = pathSwap;
				}
			}
		}
		// bubblesort end
		return sortedPath;
	}

	/**
	 * analisi di un profilo
	 *
	 * @param imp1           Immagine da analizzare
	 * @param vetRefPosition Coordinate riferimento posizionato dall'operatore [
	 *                       xStart, yStart, xEnd, yEnd ]
	 * @param vetProfile     Coordinate profilo (da rototraslare)[ xStart, yStart,
	 *                       xEnd, yEnd ]
	 * @param ra1            Diametro della Roi per calcolo baseline correction
	 * @param slab           Flag true=slab, false=cuneo
	 * @param invert         Flag true=erf invertita false=erf diritta
	 * @param step           Flag true=messaggi on, false=messaggi off
	 * @param bLabelSx       Flag true= label a sx, false=label a dx
	 * @return outFwhm[0]=FWHM (mm)
	 * @return outFwhm[1]=peak position (mm)
	 */

	public double[] analProf_ORIGINAL(ImagePlus imp1, double[] vetRefPosition, double[] vetProfile, int ra1,
			boolean slab, boolean invert, boolean step, boolean bLabelSx, double dimPixel) {

		// MyLog.waitHere("sono in analProf_ORIGINAL");
		/*
		 * Aggiornamento del 29 gennaio 2007 analProf da' in uscita i valori in
		 * millimetri, anziche' in pixel
		 */

		// -----------------------------------
//		StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
//		StackTraceElement e = stacktrace[1];// coz 0th will be getStackTrace so 1st
//		IJ.log("ESEGUO >>> " + e.getMethodName());
		// -----------------------------------

		if (imp1 == null) {
			return null;
		}

		int mra = ra1 / 2;
		double[] msd1; // vettore output rototrasl coordinate
		msd1 = UtilAyv.coord2D2(vetRefPosition, vetProfile[0] - mra, vetProfile[1] - mra, false);
		int c2x = (int) msd1[0]; // coord rototrasl centro roi sx
		int c2y = (int) msd1[1]; // coord rototrasl centro roi sx

		// nota tecnica: quando si definisce una ROI con setRoi (ovale o
		// rettangolare che sia) passiamo a ImageJ le coordinate dell'angolo in
		// alto a Sx del BoundingRectangle per cui dobbiamo sempre includere nei
		// calcoli il raggio Roi

		Overlay over1 = new Overlay(); // creo un overlay su cui riportare le Roi impiegate
		imp1.setOverlay(over1);

		// prima roi per baseline correction
		imp1.setRoi(new OvalRoi(c2x, c2y, ra1, ra1));
		imp1.updateAndDraw();
		over1.addElement(imp1.getRoi());
		imp1.getRoi().setStrokeColor(Color.red);
		ImageStatistics statC = imp1.getStatistics();
		if (step) {
			imp1.updateAndDraw();
			ButtonMessages.ModelessMsg(
					"<51> - primo centro c2x=" + c2x + " c2y=" + c2y + " ra1=" + ra1 + "  media=" + statC.mean + "  ",
					"CONTINUA");
		}
		msd1 = UtilAyv.coord2D2(vetRefPosition, vetProfile[2] - mra, vetProfile[3] - mra, false);
		int d2x = (int) msd1[0];
		int d2y = (int) msd1[1];

		// seconda roi per baseline correction
		imp1.setRoi(new OvalRoi(d2x, d2y, ra1, ra1));
		over1.addElement(imp1.getRoi());
		imp1.getRoi().setStrokeColor(Color.red);
		ImageStatistics statD = imp1.getStatistics();
		if (step) {
			imp1.updateAndDraw();
			ButtonMessages.ModelessMsg("<52> - secondo centro d2x=" + d2x + " d2y=" + d2y + " ra1=" + ra1 + "  media="
					+ statD.mean + "   <52>", "CONTINUA");
		}
		// inizio wideline
		msd1 = UtilAyv.coord2D2(vetRefPosition, vetProfile[0], vetProfile[1], false);
		c2x = (int) msd1[0];
		c2y = (int) msd1[1];
		// fine wideline
		msd1 = UtilAyv.coord2D2(vetRefPosition, vetProfile[2], vetProfile[3], false);
		d2x = (int) msd1[0];
		d2y = (int) msd1[1];

		// linea calcolo segnale mediato
		Line.setWidth(11);

		double[] profiM1 = p6rmn_COMMON.getLinePixels_COMMON(imp1, c2x, c2y, d2x, d2y);
		over1.addElement(imp1.getRoi());
//		imp1.getRoi().setStrokeColor(Color.yellow);

		if (step) {
			imp1.updateAndDraw();
			msgWideline();
			createPlot2_ORIGINAL(profiM1, dimPixel, true, bLabelSx, "ORIGINAL Profilo mediato", false);
			msgSlab();
		}

		double[] profiB1 = baselineCorrection_ORIGINAL(profiM1, statC.mean, statD.mean, step);

		if (step) {
			createPlot2_ORIGINAL(profiB1, dimPixel, true, bLabelSx, "ORIGINAL Profilo mediato + baseline correction",
					true);
			msgBaseline();
		}

		int isd3[];
		double[] outFwhm;
		if (slab) {
			// isd3 = analPlot1_ORIGINAL(profiB1, slab);
			// isd3= p6rmn_COMMON.analPlot1_ORIGINAL_COMMON(profiB1, slab);
			isd3 = p6rmn_COMMON.analPlot1_MODIF_COMMON(profiB1, slab);

			outFwhm = calcFwhm_ORIGINAL(isd3, profiB1, slab, dimPixel);

			if (step) {
				createPlot2_ORIGINAL(profiB1, dimPixel, slab, bLabelSx, "ORIGINAL plot mediato + baseline + FWHM",
						true);
				msgFwhm();
			}
			Line.setWidth(1);
			over1.clear();
			return (outFwhm);

		} else {
			double[] profiE1 = createErf_ORIGINAL(profiB1, invert); // profilo con ERF

			// isd3 = analPlot1_ORIGINAL(profiE1, slab);
			// isd3= p6rmn_COMMON.analPlot1_ORIGINAL_COMMON(profiE1, slab);
			isd3 = p6rmn_COMMON.analPlot1_MODIF_COMMON(profiE1, slab);

			outFwhm = calcFwhm_ORIGINAL(isd3, profiE1, slab, dimPixel);

			if (step) {
				createPlot2_ORIGINAL(profiE1, dimPixel, slab, bLabelSx, "ORIGINAL plot ERF con smooth 3x3 e FWHM",
						true);
				msgErf();
			}
			Line.setWidth(1);
			over1.clear();
			return (outFwhm);
		}

	}

	/**
	 * --------------------------------------------------------------
	 *
	 * MAIN ROUTINE
	 *
	 * --------------------------------------------------------------
	 *
	 * analisi di un profilo
	 *
	 * @param imp1           Immagine da analizzare
	 * @param vetRefPosition Coordinate riferimento posizionato dall'operatore [
	 *                       xStart, yStart, xEnd, yEnd ]
	 * @param vetProfile     Coordinate profilo (da rototraslare)[ xStart, yStart,
	 *                       xEnd, yEnd ]
	 * @param ra1            Diametro della Roi per calcolo baseline correction
	 * @param slab           Flag true=slab, false=cuneo
	 * @param invert         Flag true=erf invertita false=erf diritta
	 * @param step           Flag true=messaggi on, false=messaggi off
	 * @param bLabelSx       Flag true= label a sx, false=label a dx
	 * @return outFwhm[0]=FWHM (mm)
	 * @return outFwhm[1]=peak position (mm)
	 */

	public double[] analProf_FITTER(ImagePlus imp1, double[] vetRefPosition, double[] vetProfile, int ra1, boolean slab,
			boolean invert, boolean step, boolean bLabelSx, double dimPixel1) {

		/*
		 * ###########################################################################
		 * ###########################################################################
		 * ###########################################################################
		 * ###########################################################################
		 * ###########################################################################
		 *
		 *
		 * VEDI IL NOME DELLA ROUTINE E RIFLETTI
		 *
		 * Aggiornamento del 29 gennaio 2007 analProf da' in uscita i valori in
		 * millimetri, anziche' in pixel
		 *
		 * Aggiornamento del 25 aprile 2024 esperimento utilizzando SPLINE per la
		 * BASELINE CORRECTION (poi abbandonato in favore del CurveFitter)
		 *
		 * Aggiornamento del 08 maggio 2024 esperimento utilizzando il CURVE_FITTING sia
		 * per la per la BASELINE CORRECTION (POLY2), la definizione del picco e la FWHM
		 * (GAUSSIAN_NOOFFSET oppure SUPER-GAUSSIAN)
		 *
		 *
		 * ###########################################################################
		 * ###########################################################################
		 * ###########################################################################
		 * ###########################################################################
		 * ###########################################################################
		 */

		if (imp1 == null) {
			return null;
		}

		int mra = ra1 / 2;
		double[] msd1; // vettore output rototrasl coordinate
		msd1 = UtilAyv.coord2D2(vetRefPosition, vetProfile[0] - mra, vetProfile[1] - mra, false);
		int c2x = (int) msd1[0]; // coord rototrasl centro roi sx
		int c2y = (int) msd1[1]; // coord rototrasl centro roi sx
		int d2x = -1; // coord rototrasl centro roi sx
		int d2y = -1; // coord rototrasl centro roi sx

		// nota tecnica: quando si definisce una ROI con setRoi (ovale o
		// rettangolare che sia) passiamo a ImageJ le coordinate dell'angolo in
		// alto a Sx del BoundingRectangle per cui dobbiamo sempre includere nei
		// calcoli il raggio Roi

		// inizio wideline
		msd1 = UtilAyv.coord2D2(vetRefPosition, vetProfile[0], vetProfile[1], false);
		c2x = (int) msd1[0];
		c2y = (int) msd1[1];
		// fine wideline
		msd1 = UtilAyv.coord2D2(vetRefPosition, vetProfile[2], vetProfile[3], false);
		d2x = (int) msd1[0];
		d2y = (int) msd1[1];

		// linea calcolo segnale mediato sulle 11 linee
		Line.setWidth(11);

		double[] out1 = p6rmn_COMMON.getLinePixels_COMMON(imp1, c2x, c2y, d2x, d2y);
		double[][] profi1 = ProfileUtils.encode(out1);
		Line.setWidth(1);

		if (step) {
			spyname = "P001 - PROFILO MEDIATO SU 11 LINEE";
			Plot plot3 = MyPlot.plot1(profi1, spyname);
			ImagePlus imp3 = plot3.show().getPlot().getImagePlus();
			if (SPY) {
				p6rmn_COMMON.saveDebugImage_COMMON(imp3, spydir, spyname, contaxx++, SPY);
			}
			msgContinua("<61> - " + spyname);
			// MyLog.waitHere(spyname + " " + "@" + IJ.pad(contaxx++, 3));
		}

		double[][] profi11 = profi1.clone();

		if (step) {
			MyLog.logMatrix(profi11, "profi11");
		}

		boolean step1 = step;

		// ===============================================================================
		// se decideremo di fare comunque la baselinecorrection anche per il cuneo,
		// converrebbe fare anche le due ROI all'inizio ed alla fine della linea. Poi si
		// usa la ROI per il lato a 90 gradi del cuneo. Usandolo pero'come unico singolo
		// punto della linea da quel lato. In tal caso qui va messa la routine per la
		// baseline correction, gia'provata qui
		// ===============================================================================

		// MyLog.logMatrix(profi11, "profi11");

		// MyLog.waitHere("BASELINE EFFETTUATA");

		double[][] profi9 = null;

		if (slab) {

			spyname = " PROFILO ORIGINALE";
			Plot plot2 = MyPlot.plot1(profi11, spyname);
			ImagePlus imp2 = plot2.show().getPlot().getImagePlus();

//			// caso della slab, mi limito a copiare i dati su profi9
//			profi9 = ProfileUtils.devStanMobile(profi11, 10);
//			spyname = " PROFILO DEVIAZIONE STANDARD";
//			Plot plot3 = MyPlot.plot1(profi9, spyname);
//			ImagePlus imp3 = plot3.show().getPlot().getImagePlus();
			// MyLog.waitHere("VISUALIZZA I DUE PLOT");
			profi9 = profi11;

		} else {
			// caso del cuneo, calcolo la ERF tentando di fare la background correction
			// PRIMA di fare la ERF

			double[][] profiERF = createERF_FITTER(profi11, step); // profilo con ERF
			if (step) {
				spyname = "P039 - PROFILO ERF CUNEO CON PICCO RIMOSSO";
				Plot plot3 = MyPlot.plot1(profiERF, spyname);
				ImagePlus imp3 = plot3.show().getPlot().getImagePlus();
				if (SPY) {
					p6rmn_COMMON.saveDebugImage_COMMON(imp3, spydir, spyname, contaxx++, SPY);
				}

				MyLog.waitHere(spyname);
			}
			profi9 = profiERF;
		}

		double[][] profi13 = baselineCorrection_FITTER(imp1, profi9, step1);

		//
		// da qui proseguo sia per slab che per cuneo
		//

		double[][] profi10 = p6rmn_COMMON.profiInverter_COMMON(profi13);
		// double[][] profi10 = profi9;

		// ora che ho sottratto la baseline posso eseguire finalmente il fit del picco

		double[] outFWHM1 = null;
		double[] outFWHM2 = null;

		if (P6_GAUSS) {
			IJ.log("ESEGUO peakFitterFWHM_GAUSSIAN");
		}
		outFWHM1 = peakFitterFWHM_GAUSSIAN(imp1, profi10, slab, step);

		if (P6_SUPERGAUSS) {
			IJ.log("ESEGUO peakFitterFWHM_SUPERGAUSSIAN");
		}
		outFWHM2 = peakFitterFWHM_SUPERGAUSSIAN(imp1, profi10, slab, step);

//		double sTeorico = (double) ReadDicom.readFloat(
//				ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_SLICE_THICKNESS), 1));
//		double dimPixel = (double) ReadDicom
//				.readFloat(ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 1));
		//// =================================================================================
		//// NON MI PIACE AFFATTO IL CREATEPLOT2SPECIAL, FA DA SOLO UNA SECONDA VOLTA
		//// TUTTO IL RESTO, MA MI RASSEGNO, SIGH
		//// =================================================================================

//		int[] isd3 = analPlot1(profi13, slab);
//		double[] outFwhm = calcFwhmTraditional(isd3, profi13, slab, dimPixel);

		// MyLog.logVector(outFwhm, "outFwhm");

//		if (step) {
//			// ImagePlus imp5 = createPlot2(profiB1, true, bLabelSx, "Profilo mediato +
//			// baseline correction", true);
//			if (!SPY)
//				msgBaseline();
//			// saveDebugImage(imp5, spyfirst, spysecond, spyname);
//		}

		double[] outFWHMtotal = new double[10];
		if (P6_SUPERGAUSS) {
			outFWHMtotal[5] = outFWHM2[0]; // fwhmContata_SUPERGAUSSIAN
			outFWHMtotal[6] = outFWHM2[1]; // fwhmFormula_SUPERGAUSSIAN
			outFWHMtotal[7] = outFWHM2[2]; // peakX__SUPERGAUSSIAN
			outFWHMtotal[8] = outFWHM2[3]; // peakX__SUPERGAUSSIAN
			outFWHMtotal[9] = outFWHM2[4]; // rsq_SUPERGAUSSIAN

		}

		if (P6_GAUSS) {

			outFWHMtotal[0] = outFWHM1[0]; // fwhmContata_GAUSSIAN
			outFWHMtotal[1] = outFWHM1[1]; // fwhmFormula_GAUSSIAN
			outFWHMtotal[2] = outFWHM1[2]; // peakX_GAUSSIAN
			outFWHMtotal[3] = outFWHM1[3]; // peakY_GAUSSIAN
			outFWHMtotal[4] = outFWHM1[4]; // rsq_GAUSSIAN
		}

		return outFWHMtotal;
	}

	public int[] analPlot1_ORIGINAL(double[] profile1, boolean bSlab) {
		String title = "";
		int[] out1 = p6rmn_COMMON.analPlot1_ORIGINAL_COMMON(profile1, bSlab);
		return out1;
	}

	public int[] analPlot1_ORIGINAL(double[] profile1, boolean bSlab, String title) {

		int sopra1 = 0;
		int sotto1 = 0;
		int sopra2 = 0;
		int sotto2 = 0;
		double max1 = 0;
		int i1;
		int[] isd;
		isd = new int[4];
		isd[0] = 0;
		isd[1] = 0;
		isd[2] = 0;
		isd[3] = 0;
		int len1 = profile1.length;

		if (title != "") {
			IJ.log(title);
		}

		// MyLog.logVectorVertical(profile1, "profile1");

		double[] a = Tools.getMinMax(profile1);

		// MyLog.logVectorVertical(a, "a");

		double min1 = a[0];
		if (bSlab) {
			max1 = a[1];
		} else {
			max1 = 0;
		}

		// calcolo meta' altezza
		double half = (max1 - min1) / 2 + min1;
		// IJ.log("half= " + half);
		// ricerca valore < half partendo da SX
		for (i1 = 0; i1 < len1 - 1; i1++) {
			if (profile1[i1] < half) {
				sotto1 = i1;
				break;
			}
		}
		// torno indietro e cerco il primo valore > half
		for (i1 = sotto1; i1 > 0; i1--) {
			if (profile1[i1] > half) {
				sopra1 = i1;
				break;
			}
		}
		// ricerca valore < half partendo da DX
		for (i1 = len1 - 1; i1 > 0; i1--) {
			if (profile1[i1] < half) {
				sotto2 = i1;
				break;
			}
		}
		// torno indietro e cerco il primo valore > half
		for (i1 = sotto2; i1 < len1 - 1; i1++) {
			if (profile1[i1] > half) {
				sopra2 = i1;
				break;
			}
		}
		isd[0] = sotto1;
		isd[1] = sopra1;
		isd[2] = sotto2;
		isd[3] = sopra2;
//		MyLog.logVector(isd, "isd");
//		MyLog.waitHere();
		return isd;
	} // analPlot1

	/**
	 *
	 * @param profile1
	 * @param step
	 * @return
	 */
	public int[] analPlot1_FITTER(double[] profile1, boolean step) {
		String title = "";
		int[] out1 = analPlot1_FITTER(profile1, step, title);
		return out1;
	}

	public int[] analPlot1_FITTER(double[] profile1, boolean step, String title) {
		int sopra1 = 0;
		int sotto1 = 0;
		int sopra2 = 0;
		int sotto2 = 0;
		double max1 = 0;
		double min1 = 0;
		int i1;
		int[] isd;
		isd = new int[4];
		isd[0] = 0;
		isd[1] = 0;
		isd[2] = 0;
		isd[3] = 0;
		int len1 = profile1.length;
		double[] a = Tools.getMinMax(profile1);

		if (title != "") {
			IJ.log(title);
		}

		if (Math.abs(a[1]) > Math.abs(a[0])) {
			max1 = a[1];
			min1 = a[0];
		} else {
			max1 = a[0];
			min1 = a[1];
		}

		// calcolo meta' altezza
		double half = (max1 - min1) / 2 + min1;
		double[] half2 = new double[4];
		half2[0] = 0;
		half2[1] = half;
		half2[2] = profile1.length;
		half2[3] = half;

		if (false) {
			double[][] profile11 = ProfileUtils.encode(profile1);
			Plot plot2 = p6rmn_COMMON.createPlot1_COMMON(profile11, half2, true, true, true, (title + " NO FWHM"), 5.0,
					0.75);
			plot2.show();
		}

		// a seconda che half sia positiva o negativa devo fare due ricerche diverse
		if (half < 0) {
			// ricerca valore < half partendo da SX
			for (i1 = 0; i1 < len1 - 1; i1++) {
				if (profile1[i1] < half) {
					sotto1 = i1;
					break;
				}
			}
			// torno indietro e cerco il primo valore > half
			for (i1 = sotto1; i1 > 0; i1--) {
				if (profile1[i1] > half) {
					sopra1 = i1;
					break;
				}
			}
			// ricerca valore < half partendo da DX
			for (i1 = len1 - 1; i1 > 0; i1--) {
				if (profile1[i1] < half) {
					sotto2 = i1;
					break;
				}
			}
			// torno indietro e cerco il primo valore > half
			for (i1 = sotto2; i1 < len1 - 1; i1++) {
				if (profile1[i1] > half) {
					sopra2 = i1;
					break;
				}
			}
		} else {
			// ricerca valore > half partendo da SX
			for (i1 = 0; i1 < len1 - 1; i1++) {
				if (profile1[i1] > half) {
					sotto1 = i1;
					break;
				}
			}
			for (i1 = sotto1; i1 > 0; i1--) {
				if (profile1[i1] < half) {
					sopra1 = i1;
					break;
				}
			}
			for (i1 = len1 - 1; i1 > 0; i1--) {
				if (profile1[i1] > half) {
					sotto2 = i1;
					break;
				}
			}
			for (i1 = sotto2; i1 < len1 - 1; i1++) {
				if (profile1[i1] < half) {
					sopra2 = i1;
					break;
				}
			}

		}
		isd[0] = sotto1;
		isd[1] = sopra1;
		isd[2] = sotto2;
		isd[3] = sopra2;
		return isd;
	} // analPlot1

	/**
	 * Inverte il segnale del profilo
	 *
	 * @param profile1
	 * @return
	 */
//	public double[][] profiInverter(double[][] profile1) {
//
//		double[] profileX = ProfileUtils.decodeX(profile1);
//		double[] profileY = ProfileUtils.decodeY(profile1);
//
//		// double max = ArrayUtils.vetMax(profileY);
//		// double min = ArrayUtils.vetMin(profileY);
//
//		for (int i1 = 0; i1 < profileY.length; i1++) {
//			profileY[i1] = profileY[i1] * (-1);
//		}
//
//		return ProfileUtils.encode(profileX, profileY);
//	}

	/**
	 * Trasforma il profilo Y aggiungendo i valori X
	 *
	 * @param profi1
	 * @return
	 */
//	public double[][] profiTransform(double[] profi1) {
//
//		double[][] out1 = new double[profi1.length][2];
//		for (int i1 = 0; i1 < profi1.length; i1++) {
//			out1[i1][0] = (double) i1;
//			out1[i1][1] = (double) profi1[i1];
//		}
//		return out1;
//	}

	public double[] calcFwhm_FITTERmm(int[] isd, double[] profile, boolean bslab, double dimPixel, boolean step) {

		String title = "";
		MyLog.waitThere("calcFwhmFITTERmm");
		double[] out1 = calcFwhm_FITTERmm(isd, profile, bslab, dimPixel, step, title);
		return out1;
	}

	/**
	 * calcolo FWHM del profilo assegnato
	 *
	 * @param isd     coordinate sul profilo sopra e sotto met� altezza
	 * @param profile profilo da analizzare
	 * @param bslab   true se slab, false se cuneo
	 * @return out[0] fwhm calcolata (mm)
	 * @return out[1] peak position (mm)
	 */
	public double[] calcFwhm_FITTERmm(int[] isd, double[] profile, boolean bslab, double dimPixel, boolean step,
			String title) {

		IJ.showStatus("calcFwhmFITTERmm");

		double peak = 0;
		double[] a = Tools.getMinMax(profile);
		double min1 = a[0];
		double max1 = a[1];
		if (Math.abs(max1) > Math.abs(min1)) {
			max1 = a[1];
			min1 = a[0];
		} else {
			max1 = a[0];
			min1 = a[1];
		}

		// MyLog.waitHere("GUARDA QUEL CAZZO DI PLOT!");

		// interpolazione lineare sinistra
		double px0 = isd[0];
		double px1 = isd[1];
		double py0 = profile[isd[0]];
		double py1 = profile[isd[1]];
		double py2 = (max1 - min1) / 2.0 + min1;
		double px2 = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);
		double sx = px2;
		// interpolazione lineare destra
		px0 = isd[2];
		px1 = isd[3];
		py0 = profile[isd[2]];
		py1 = profile[isd[3]];
		py2 = (max1 - min1) / 2.0 + min1;
		px2 = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);
		double dx = px2;

		MyLog.waitHere("sx= " + sx + " dx= " + dx);

		double fwhm = (dx - sx) * dimPixel;

		for (int i1 = 0; i1 < profile.length; i1++) {
			if (profile[i1] == max1) {
				peak = i1 * dimPixel;
			}
		}
		double[] out = { fwhm, peak };
//		IJ.log("calcFwhm_FITTER sx= " + sx + " dx= " + dx + " fwhm= " + fwhm + " max1= " + max1 + "  min1=  " + min1
//				+ " peak= " + peak);
//		MyLog.waitHere();

		return (out);
	} // calcFwhm

	public double[] calcFwhm_FITTERpix(int[] isd, double[] profile, boolean bslab, double dimPixel, boolean step) {

		String title = "";
		double[] out1 = calcFwhm_FITTERpix(isd, profile, bslab, dimPixel, step, title);
		return out1;
	}

	/**
	 * calcolo FWHM del profilo assegnato
	 *
	 * @param isd     coordinate sul profilo sopra e sotto met� altezza
	 * @param profile profilo da analizzare
	 * @param bslab   true se slab, false se cuneo
	 * @return out[0] fwhm calcolata (mm)
	 * @return out[1] peak position (mm)
	 */
	public double[] calcFwhm_FITTERpix(int[] isd, double[] profile, boolean bslab, double dimPixel, boolean step,
			String title) {

		IJ.showStatus("calcFwhmFITTERpix");

		double peak = 0;
		double[] a = Tools.getMinMax(profile);
		double min1 = a[0];
		double max1 = a[1];
		if (Math.abs(max1) > Math.abs(min1)) {
			max1 = a[1];
			min1 = a[0];
		} else {
			max1 = a[0];
			min1 = a[1];
		}

		// MyLog.waitHere("GUARDA QUEL CAZZO DI PLOT!");

		// interpolazione lineare sinistra
		double px0 = isd[0];
		double px1 = isd[1];
		double py0 = profile[isd[0]];
		double py1 = profile[isd[1]];
		double py2 = (max1 - min1) / 2.0 + min1;
		double px2 = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);
		double sx = px2;
		// interpolazione lineare destra
		px0 = isd[2];
		px1 = isd[3];
		py0 = profile[isd[2]];
		py1 = profile[isd[3]];
		py2 = (max1 - min1) / 2.0 + min1;
		px2 = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);
		double dx = px2;

		// MyLog.waitHere("sx= "+sx+ " dx= "+dx);

		double fwhm = dx - sx;

		for (int i1 = 0; i1 < profile.length; i1++) {
			if (profile[i1] == max1) {
				peak = i1;
			}
		}
		double[] out = { fwhm, peak };
//		IJ.log("calcFwhm_FITTER sx= " + sx + " dx= " + dx + " fwhm= " + fwhm + " max1= " + max1 + "  min1=  " + min1
//				+ " peak= " + peak);
//		MyLog.waitHere();

		return (out);
	} // calcFwhm

	/**
	 * calcolo FWHM del profilo assegnato
	 *
	 * @param isd     coordinate sul profilo sopra e sotto meta' altezza
	 * @param profile profilo da analizzare
	 * @param bslab   true se slab, false se cuneo
	 * @return out[0] fwhm calcolata (mm)
	 * @return out[1] peak position (mm)
	 */
	public double[] calcFwhm_ORIGINAL(int[] isd, double[] profile, boolean bslab, double dimPixel) {

		String title = "";
		double[] out1 = calcFwhm_ORIGINAL(isd, profile, bslab, dimPixel, title);
		return out1;
	}

	public double[] calcFwhm_ORIGINAL(int[] isd, double[] profile, boolean bslab, double dimPixel, String title) {

		if (title != "") {
			IJ.log(title);
		}
		double peak = 0;
		double[] a = Tools.getMinMax(profile);
		double min1 = a[0];
		double max1 = 0;
		if (bslab) {
			max1 = a[1];
		} else {
			max1 = 0;
		}
		// interpolazione lineare sinistra
		double px0 = isd[0];
		double px1 = isd[1];
		double py0 = profile[isd[0]];
		double py1 = profile[isd[1]];
		double py2 = (max1 - min1) / 2.0 + min1;
		double px2 = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);
		double sx = px2;
		// interpolazione lineare destra
		px0 = isd[2];
		px1 = isd[3];
		py0 = profile[isd[2]];
		py1 = profile[isd[3]];
		py2 = (max1 - min1) / 2.0 + min1;
		px2 = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);
		double dx = px2;
		double fwhm = (dx - sx) * dimPixel;

		for (int i1 = 0; i1 < profile.length; i1++) {
			if (profile[i1] == min1) {
				peak = i1 * dimPixel;
			}
		}
		double[] out = { fwhm, peak };
		return (out);
	} // calcFwhm

	/**
	 * calcolo FWHM del profilo assegnato
	 *
	 * @param isd     coordinate sul profilo sopra e sotto meta' altezza
	 * @param profile profilo da analizzare
	 * @param bslab   true se slab, false se cuneo
	 * @return out[0] fwhm calcolata (mm)
	 * @return out[1] peak position (mm)
	 */
	public double[] calcFwhm_ORIGINAL_MODIFIED(int[] isd, double[] profile, boolean bslab, double dimPixel) {

		String title = "";
		double[] out1 = calcFwhm_ORIGINAL_MODIFIED(isd, profile, bslab, dimPixel, title);
		return out1;
	}

	public double[] calcFwhm_ORIGINAL_MODIFIED(int[] isd, double[] profile, boolean bslab, double dimPixel,
			String title) {

		if (title != "") {
			IJ.log(title);
		}
		double peak = 0;
		double[] a = Tools.getMinMax(profile);
		double min1 = a[0];
		double max1 = 0;
		if (bslab) {
			max1 = a[1];
		} else {
			max1 = 0;
		}
		// interpolazione lineare sinistra
		double px0 = isd[0];
		double px1 = isd[1];
		double py0 = profile[isd[0]];
		double py1 = profile[isd[1]];
		double py2 = (max1 - min1) / 2.0 + min1;
		double px2 = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);
		double sx = px2;
		// interpolazione lineare destra
		px0 = isd[2];
		px1 = isd[3];
		py0 = profile[isd[2]];
		py1 = profile[isd[3]];
		py2 = (max1 - min1) / 2.0 + min1;
		px2 = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);
		double dx = px2;
		double fwhm = (dx - sx) * dimPixel;

		for (int i1 = 0; i1 < profile.length; i1++) {
			if (profile[i1] == min1) {
				peak = i1 * dimPixel;
			}
		}
		double[] out = { fwhm, peak };
		return (out);
	} // calcFwhm

	/**
	 * assegna un profilo su cui effettuare i calcoli
	 *
	 * @param imp1 immagine su cui effettuare il profilo
	 * @param x1   coordinata x start profilo
	 * @param y1   coordinata y start profilo
	 * @param x2   coordinata x end profilo
	 * @param y2   coordinata y end profilo
	 * @return profilo wideline
	 */
//	public double[] getLinePixels_ORIGINAL(ImagePlus imp1, int x1, int y1, int x2, int y2) {
//		imp1.setRoi(new Line(x1, y1, x2, y2));
//		imp1.updateAndDraw();
//		Roi roi1 = imp1.getRoi();
//		double[] profiM1 = ((Line) roi1).getPixels();
//		return profiM1;
//	}

	/**
	 * calcolo baseline correction del profilo assegnato
	 *
	 * @param imp1     immagine di origine
	 * @param profile1 profilo da analizzare
	 * @param step
	 * @return
	 */
	public double[][] baselineCorrection_FITTER(ImagePlus imp1, double[][] profile1, boolean step) {

		IJ.showStatus("baselineCorrectionFITTER");

		int len1 = profile1.length;
		boolean smooth = true;

		int[] out2 = localizzatoreDerivata(profile1, smooth, step);
		int sx = out2[0];
		int dx = out2[1];

		double[][] profile3 = ProfileUtils.peakRemover(profile1, sx, dx, step);

		double[] profiX = ProfileUtils.decodeX(profile3);
		double[] profiY = ProfileUtils.decodeY(profile3);

		if (step) {
			spyname = "P032 - BASELINE RIMANENTE DOPO MASK PICCO";
			Plot plot6 = MyPlot.plot1points(profile1, profiX, profiY, spyname);
			ImagePlus imp6 = plot6.show().getImagePlus();
			if (SPY) {
				p6rmn_COMMON.saveDebugImage_COMMON(imp6, spydir, spyname, contaxx++, SPY);
			}
			msgContinua("<66> - " + spyname + " (punti blu) ");
		}

		// ----------------------------------------------------------
		CurveFitter curveFitter = new CurveFitter(profiX, profiY);
		curveFitter.doFit(CurveFitter.POLY2);

		String resultString = curveFitter.getResultString();

		if (step) {
			spyname = "P036 - OUTPUT DI CURVEFITTER PER LA POLY2";
			Plot plot1 = curveFitter.getPlot();
			plot1.setColor(Color.black);
			plot1.addLabel(0.01, 0.99, spyname);
			ImagePlus imp6 = plot1.show().getImagePlus();
			if (SPY) {
				p6rmn_COMMON.saveDebugImage_COMMON(imp6, spydir, spyname, contaxx++, SPY);
			}
			msgContinua("<67> - " + spyname);
		}

		double[] fitY = new double[len1];
		double[] fitX = new double[len1];

		for (int i1 = 0; i1 < len1; i1++) {
			fitX[i1] = i1;
			fitY[i1] = curveFitter.f(i1);
		}
		double[][] baselineFit = ProfileUtils.encode(fitX, fitY);

		if (step) {
			spyname = "P037 - FIT BASELINE CON POLY2";
			Plot plot6 = MyPlot.plot2(profile1, baselineFit, spyname);
			ImagePlus imp6 = plot6.show().getImagePlus();
			if (SPY) {
				p6rmn_COMMON.saveDebugImage_COMMON(imp6, spydir, spyname, contaxx++, SPY);
			}
			msgContinua("<68> - " + spyname);
		}

		if (verbose) {
			IJ.log("-----------------------------------------");
			IJ.log("---- ESEGUO IL FIT GAUSSIAN-POLY2 ----");
			IJ.log("-----------------------------------------");
			IJ.log(resultString);
			IJ.log("-----------------------------------------");
		}

		// la spline, pero' non va sottratta brutalmente (anche se andrebbe bene lo
		// stesso)

		double[] profileCorrectY = new double[len1];
		double[] profile1Y = ProfileUtils.decodeY(profile1);
		double[] baselineFitY = ProfileUtils.decodeY(baselineFit);

		for (int i1 = 0; i1 < len1; i1++) {
			profileCorrectY[i1] = profile1Y[i1] - baselineFitY[i1];
		}

		double[] profileCorrectX = ProfileUtils.decodeX(profile1);
		double[][] profileCorrect = ProfileUtils.encode(profileCorrectX, profileCorrectY);

		if (step) {
			spyname = "P038 - PROFILO DOPO SOTTRAZIONE BASELINE";
			Plot plot6 = MyPlot.plot1(profileCorrect, spyname);
			ImagePlus imp6 = plot6.show().getImagePlus();
			if (SPY) {
				p6rmn_COMMON.saveDebugImage_COMMON(imp6, spydir, spyname, contaxx++, SPY);
			}
			msgContinua("<69> - " + spyname);
		}

		return profileCorrect;
	}

	/**
	 * calcolo baseline correction del profilo assegnato. Si trattava di una
	 * correzione lineare rispetto alle medie di due ROI circolari poste ai lati del
	 * fantoccio
	 *
	 * @param profile1 profilo da correggere
	 * @param media1   media sul fondo a sx
	 * @param media2   media sul fondo a dx
	 * @return profilo corretto
	 */
	public double[] baselineCorrection_ORIGINAL(double[] profile1, double media1, double media2, boolean step) {

		int len1 = profile1.length;
//		double[] minmax = Tools.getMinMax(profile1);
		double[] profile2 = new double[len1];
		double diff1 = (media1 - media2) / len1;
		for (int i1 = 0; i1 < len1; i1++) {
			profile2[i1] = profile1[i1] + diff1 * i1;
		}

		double[] profileX = new double[len1];
		for (int i1 = 0; i1 < len1; i1++) {
			profileX[i1] = i1;
		}
		double[] profileY = new double[len1];
		for (int i1 = 0; i1 < len1; i1++) {
			profileY[i1] = diff1 * i1;
		}
		double[][] prof1 = ProfileUtils.encode(profileX, profile1);
		double[][] prof2 = ProfileUtils.encode(profileX, profileY);

		// MyLog.waitHere("BASELINE");

		if (step) {
			spyname = "ORIGINAL - PROFILO E CORREZIONE";
			Plot plot6 = MyPlot.plot2(prof1, prof2, spyname);
			ImagePlus imp6 = plot6.show().getImagePlus();
			p6rmn_COMMON.saveDebugImage_COMMON(imp6, spydir, spyname, contaxx++, SPY);

			plot6.show();
			if (step) {
				// MyLog.waitHere(spyname);
				ButtonMessages.ModelessMsg("<60> - Profilo e correzione", "CONTINUA");
			}

		}

		return profile2;
	} // baselineCorrection

	/**
	 * Cerca di localizzare il profilo dell'oggetto, permettendo cosi' di rimuoverlo
	 * dal fondo prima del fit. Faccio l'ipotesi di avere un unico oggetto, che il
	 * pixel minimo (lavoro in negativo) faccia parte dell'oggetto. Il profilo di
	 * cui dispongo e'di 132 pixel. Restituisco solo i pixels appartenenti al fondo
	 *
	 * @param profile2
	 */
	public static int[] localizzatoreDerivata(double[][] profile2, boolean smooth, boolean step) {



		step=true;
		spyname = "P003 - INPUT LOCALIZZATORE DERIVATA";
		double[][] dd1 = profile2;
		if (step) {
			Plot plot11 = MyPlot.plot1(dd1, spyname);
			ImagePlus imp11 = plot11.show().getImagePlus();
			if (step) {
				msgContinua("<62> - " + spyname);
			}
		}

		double[][] dd141 = null;
		double[][] dd14 = null;
		if (smooth) {
			// per quello che ci serve possiamo fare uno smooth molto energico
			spyname = "P004 - SMOOTH5";

			dd141 = ProfileUtils.smooth5(profile2);
			dd14 = ProfileUtils.smooth5(dd141);
//		double[][] dd14 = profile2;

			if (SPY) {

				Plot plot14 = MyPlot.plot1(dd14, spyname);
				ImagePlus imp14 = plot14.show().getImagePlus();
				if (step) {
					msgContinua("<63> - " + spyname);
				}
			}
		} else {
			dd14 = profile2;
		}

		double[][] dd13 = ProfileUtils.derivataPrima(dd14);
		if (step) {
			spyname = "P005 - DERIVATA PRIMA";
			Plot plot13 = MyPlot.plot1(dd13, spyname);
			ImagePlus imp13 = plot13.show().getImagePlus();
			msgContinua("<64> - " + spyname);
		}


		double[][] aa1= ProfileUtils.limitiOggettoDerivataPrima(dd13);

		double[][] dd15 = ProfileUtils.smooth5(dd13);

		double[][] dd17 = ProfileUtils.derivataPrima(dd15);


		if (step) {
			spyname = "P006 - DERIVATA SECONDA";
			Plot plot17 = MyPlot.plot1(dd17, spyname);
			ImagePlus imp17 = plot17.show().getImagePlus();
			msgContinua("<65> - " + spyname);
		}

		MyLog.waitHere("CAZZABUBBOLE");

		int[] lim = ProfileUtils.zeroCrossing(dd17, spydir, spyname, contaxx++, SPY, step);

//		saveDebugImage(imp6, spydir, spyname);

//		MyLog.waitHere("limiti finali baseline min= " + lim[0] + " max= " + lim[1]);

		return lim;

	}

	/**
	 * Cerca di localizzare il profilo dell'oggetto, permettendo cosi' di rimuoverlo
	 * dal fondo prima del fit. Faccio l'ipotesi di avere un unico oggetto, che il
	 * pixel minimo (lavoro in negativo) faccia parte dell'oggetto. Il profilo di
	 * cui dispongo e'di 132 pixel. Utilizzo una finestra detector di 5 pixel.
	 * Faccio anche l'ipotesi che ai lati dell'oggetto abbioamo il fondo, largo
	 * almeno 5 pixel.
	 *
	 * @param profile1
	 */
	public static int[] localizzatoreStatistica(double[][] profile1) {

		int len1 = profile1.length;
		// double[] profileX = ProfileUtils.decodeX(profile1);
		double[] profileY = ProfileUtils.decodeY(profile1);
		double[] minMax = Tools.getMinMax(profileY);
		// double min = minMax[0];
		double max = minMax[1];
		double[] buffer = new double[5];
		boolean[] object1 = new boolean[len1];
		double mean1 = 0;
		double sd2 = ArrayUtils.vetSd(profileY); // utilizzo la deviazione standard del profilo
		double transition = max - sd2;
		boolean isteresiUP = false;
		boolean isteresiDW = true;
		int left = 9999;
		int right = 9999;

		// IJ.log("max= " + max + " min= " + min + " transition= " + transition);

		for (int i1 = 2; i1 < len1 - 2; i1++) {
			buffer[0] = profileY[i1 - 2];
			buffer[1] = profileY[i1 - 1];
			buffer[2] = profileY[i1 - 0];
			buffer[3] = profileY[i1 - 1];
			buffer[4] = profileY[i1 - 2];
			mean1 = (buffer[0] + buffer[1] + buffer[2] + buffer[3] + buffer[4]) / 5;
			if (mean1 >= transition) {
				object1[i1] = true;
			} else if (mean1 < transition) {
				object1[i1] = false;
				if (isteresiUP) {
					object1[i1 - 1] = false;
					object1[i1 - 2] = false;
					object1[i1 - 3] = false;
					object1[i1 - 4] = false;
					object1[i1 - 5] = false;
					object1[i1 - 6] = false;
					isteresiUP = false;
				}
				if (isteresiDW) {
					object1[i1 - 1] = false;
					object1[i1 - 2] = false;
					object1[i1 - 3] = false;
					object1[i1 - 4] = false;
					isteresiUP = true;
					isteresiDW = false;
				}
			}
		}

		boolean up = false;

		for (int i1 = 2; i1 < len1 - 2; i1++) {
			if (object1[i1] && !up) {
				up = true;
				left = i1;
				break;
			}
		}
		for (int i1 = 2; i1 < len1 - 2; i1++) {
			if (!object1[i1] && up) {
				up = true;
				right = i1;
				break;
			}
		}

		int[] out = new int[2];
		out[0] = left;
		out[1] = right;

		return out;

	}

	/**
	 * determinazione del picco ATTUALISSIMA utilizzando un FIT GAUSSIANO
	 *
	 * @param imp1     immagine di origine
	 * @param profile1 profilo da analizzare
	 * @param slab     true se slab false se cuneo
	 * @param step
	 * @return
	 */
	public double[] peakFitterFWHM_GAUSSIAN(ImagePlus imp1, double[][] profile1, boolean slab, boolean step) {

		// -----------------------------------
//		StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
//		StackTraceElement e = stacktrace[1];// coz 0th will be getStackTrace so 1st
//		IJ.log("ESEGUO >>> " + e.getMethodName());
		// -----------------------------------

//		IJ.log("--- PROFILE INPUT GAUSSIAN-NOOFFSET ----");
//		MyLog.logMatrix(profile1, "profileInput");

		int len1 = profile1.length;
		double[] profiX = ProfileUtils.decodeX(profile1);
		double[] profiY = ProfileUtils.decodeY(profile1);

		// ----------------------------------------------------------
		//
		CurveFitter curveFitter = new CurveFitter(profiX, profiY);
		curveFitter.doFit(CurveFitter.GAUSSIAN_NOOFFSET);

		double[] fitX = new double[len1];
		double[] fitY = new double[len1];

		for (int i1 = 0; i1 < len1; i1++) {
			fitX[i1] = i1;
			fitY[i1] = curveFitter.f(i1);
		}
		double[][] fitOut = ProfileUtils.encode(fitX, fitY);

//		double fitGoodness = curveFitter.getFitGoodness();
		String resultString = curveFitter.getResultString();

//		String statusString = curveFitter.getStatusString();

		double[] xpoints = curveFitter.getXPoints();
		double[] ypoints = curveFitter.getXPoints();
		int peakY = CurveFitter.getMax(ypoints);
		int peakX = 9999;
		for (int i1 = 0; i1 < xpoints.length; i1++) {
			if (ypoints[i1] == peakY) {
				peakX = i1;
				break;
			}
		}

		double[] params = curveFitter.getParams();
		double a1 = params[0];
		double b1 = params[1];
		double c1 = params[2];

		double rsq = curveFitter.getRSquared();
		double fwhmFormula = 2 * Math.sqrt(2 * Math.log(2)) * c1;

		float dimPixel = ReadDicom
				.readFloat(ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 1));
		double sTeorico = ReadDicom.readFloat(
				ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_SLICE_THICKNESS), 1));

		// ===================================================================================
		// calcolo la FWHM anche col vecchio metodo ORIGINAL andando a cercare i pixels
		// sopra e sotto la mezza altezza con analplot e poi calcolando la FWHM con
		// calcFWHM
		// ===================================================================================

		int[] isd = analPlot1_FITTER(fitY, step, "");
		// MyLog.logVector(isd, "GAUSSIAN isd");
		double[] outFwhmPix = calcFwhm_FITTERpix(isd, fitY, slab, dimPixel, step);
		double fwhmContata = outFwhmPix[0];
		if (SPY)
		 {
			MyLog.logVector(outFwhmPix, "GAUSSIAN outFwhm_pix");
		// MyLog.waitHere();
		// ===================================================================================
		// ===================================================================================
		}

		// devo trasformare la FWHM da pixels su di un piano inclinato di 11.3 gradi a
		// millimetri di spessore
		//
		// FWHM [mm]= FWHM * dimPixel * Math.sin(Math.toRadians(11.3)
		//
		// per la immagine di test ho che il dimPixel=0.78125
		//

		spyname = "P031 - OUTPUT DI CURVEFITTER PER LA GAUSSIANA";

		if (SPY) {
			Plot plot1 = curveFitter.getPlot();
			plot1.setColor(Color.black);
			plot1.addLabel(0.01, 0.99, spyname);
			ImagePlus imp6 = plot1.show().getImagePlus();
			p6rmn_COMMON.saveDebugImage_COMMON(imp6, spydir, spyname, contaxx++, SPY);

			if (step) {
				msgContinua("<70> - " + spyname);
			}

			// MyLog.waitHere(spyname + " " + "@" + IJ.pad(contaxx++, 3));
			plot1.show();
		}

		if (verbose) {
			IJ.log("-----------------------------------------");
			IJ.log("---- ESEGUO IL FIT GAUSSIAN-NOOFFSET ----");
			IJ.log("-----------------------------------------");
			IJ.log(resultString);
			IJ.log("-----------------------------------------");
		}
		// IJ.log("fitGoodness= " + fitGoodness);
		// IJ.log("FWHM [pix]= " + FWHM);
		// IJ.log("dimPixel= " + dimPixel);
		// IJ.log("corrAngolo 11.3= " + Math.sin(Math.toRadians(11.3)));
//		if (slab)
//			IJ.log("------------------ GAUSSIAN SLAB --------------------");
//		if (!slab)
//			IJ.log("------------------ GAUSSIAN WEDGE -------------------");
//		IJ.log("R^2= " + rsq + " FWHM [mm]= " + FWHM * dimPixel * Math.sin(Math.toRadians(11.3)));

//		IJ.log("KAPPA= " + dimPixel * Math.sin(Math.toRadians(11.3)));

//		IJ.log("-----------------------------------------------------");

		// MyLog.waitHere("Calcolo da fit gaussiana FWHM [mm]= " + FWHM * dimPixel *
		// Math.sin(Math.toRadians(11.3)));

		//
		// ----------------------------------------------------------
		// ----------------------------------------------------------
		// ----------------------------------------------------------
		// ----------------------------------------------------------

		double[] outFWHM = new double[5];
		outFWHM[0] = fwhmContata;
		outFWHM[1] = fwhmFormula;
		outFWHM[2] = peakX;
		outFWHM[3] = peakY;
		outFWHM[4] = rsq;

		if (SPY) {
			spyname = "P036 - FWHM CALCOLATA SUL FIT GAUSSIANO";
			Plot plot6 = p6rmn_COMMON.createPlot1_COMMON(fitOut, outFWHM, slab, true, false, spyname, sTeorico,
					dimPixel);
			ImagePlus imp6 = plot6.show().getImagePlus();
			p6rmn_COMMON.saveDebugImage_COMMON(imp6, spydir, spyname, contaxx++, SPY);

			if (step)
			 {
				msgContinua("<71> - " + spyname);
			// MyLog.waitHere(spyname + " " + "@" + IJ.pad(contaxx++, 3));
			}
		}

		return outFWHM;
	}

	/**
	 * determinazione del picco ATTUALISSIMA utilizzando un FIT SUPERGAUSSIANO
	 *
	 * @param imp1     immagine di origine
	 * @param profile1 profilo da analizzare
	 * @param slab     true se slab false se cuneo
	 * @param step
	 * @return
	 */
	public double[] peakFitterFWHM_SUPERGAUSSIAN(ImagePlus imp1, double[][] profile1, boolean slab, boolean step) {

		return peakFitterFWHM_SUPERGAUSSIAN(imp1, profile1, slab, step, false, 10, null);
	}

	public double[] peakFitterFWHM_SUPERGAUSSIAN(ImagePlus imp1, double[][] profile1, boolean slab, boolean step,
			boolean test, int maxgiri, double[] outGuessing) {

//		IJ.log("--- PROFILE INPUT SUPERGAUSSIAN ----");
//		if (SPY)
//			MyLog.logMatrix(profile1, "profileInput");

		// -----------------------------------
//		StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
//		StackTraceElement e = stacktrace[1];// coz 0th will be getStackTrace so 1st
//		IJ.log("ESEGUO >>> " + e.getMethodName());
		// -----------------------------------

		CurveFitter curveFitter = null;
		double[] initialGuessing = null;
		boolean done = false;
		boolean last = false;

		int len1 = profile1.length;

		double[] profiX = ProfileUtils.decodeX(profile1);
		double[] profiY = ProfileUtils.decodeY(profile1);

		// y = a + (b-a) exp(-(x-c) (x-c)/(2 d d))

//		String superGaussianFormula1 = "y = a + (b-a) * exp(-log(2)*(pow(4*(x-c)*(x-c)/ (d*d), e ) ) )";
//		String superGaussianFormula2 = "y = a + b * exp(-1*(pow((x-c),e)/(2*pow(d, e ) ) ) )";
		String superGaussianFormula3 = "y= a + b * exp(-1*(pow((x-c)*(x-c),e)/pow(2*d*d,e)))";
//		String superGaussianFormula4 = " y = a+(b-a)exp(-(x-c)*(x-c)/(2*d*d))";

		// ----------------------------------------------------------
//		double[] initialGuessing0 = new double[5];
//		initialGuessing0[0] = ArrayUtils.vetMin(profiY);
//		initialGuessing0[1] = ArrayUtils.vetMax(profiY);
//		initialGuessing0[2] = ArrayUtils.vetMean(profiY);
//		initialGuessing0[3] = ArrayUtils.vetSd(profiY);
//		initialGuessing0[4] = +3.0;

		double[] initialGuessing1 = new double[5];
		initialGuessing1[0] = ArrayUtils.vetMin(profiY);
		initialGuessing1[1] = ArrayUtils.vetMax(profiY);
		initialGuessing1[2] = profiY.length / 2;
		initialGuessing1[3] = Math.sqrt(ArrayUtils.vetSd(profiY));
		initialGuessing1[4] = +2.0;

		if (test) {
			initialGuessing = outGuessing;
		} else {
			initialGuessing = initialGuessing1;
		}

		boolean showSettings = false;
//		boolean lavora = false;
//		boolean uno = false;
//		boolean due = false;

		double rsq = 0;
		double gof = 0;
		double rsqmax = 0;
		double guessmax = 0;

		int iteraz = 0;
		curveFitter = new CurveFitter(profiX, profiY);
//		curveFitter.setMaxError(1.00E-16);
//		curveFitter.setMaxIterations(1000);
//		curveFitter.setRestarts(1000);
		int ciclo = 0;
		double accetta = 0.926;
		double lastrsq = 0;

		do {
			// anziche' effettuare un numero fisso di iterazioni, prima di rinunciare, mi
			// basta cjhe per 3 volte il valore di rsq (R^2) non migliori e rimanga
			// costante. A quel punto scelgo il valore del parametro che aveva dato il
			// miglior risultatao.

			curveFitter.doCustomFit(superGaussianFormula3, initialGuessing, showSettings);
			iteraz++;
			if (SPY) {
				Plot plotFit = curveFitter.getPlot();
				plotFit.show();
			}

			rsq = curveFitter.getRSquared();
			gof = curveFitter.getFitGoodness();
			if (last) {
				done = true;
			}
			if (!done) {
				if (iteraz > 0) {
					initialGuessing[4] += 0.4;
					if (SPY) {
						IJ.log("iterazione= " + iteraz + " rsq= " + rsq + " gof= " + gof);
					}
					if (SPY) {
						MyLog.logVector(initialGuessing, "initialGuessing");
					}
				}

				if (rsq > rsqmax) {
					rsqmax = rsq;
					guessmax = initialGuessing[4];
				}

//				due = iteraz <= maxgiri;

				if (rsq <= accetta) {
					if (lastrsq == rsq) {
						ciclo++;
					} else {
						ciclo = 0;
						done = false;
						lastrsq = rsq;
					}
					if (ciclo == 3) {
						last = true;
						if (SPY) {
							IJ.log("======= eseguo ultimo fit con guessmax= " + guessmax + " ======");
						}
//						MyLog.logMatrix(profile1, "profile1");
//						MyLog.logVector(initialGuessing, "initialGuessing");
//						MyLog.waitHere();
						initialGuessing[4] = guessmax;
					}
					if (SPY) {
						IJ.log("iteraz= " + iteraz + " ciclo= " + ciclo);
					}
				} else {
					done = true;
				}
			}

		} while (!done);

		if (!done && (step || SPY)) {
			MyLog.waitHere("FALLIMENTO CALCOLO SPESSORE rsq= " + rsq + " DOPO " + iteraz + " ITERAZIONI");
		}

		if (SPY)

		{
			IJ.log("------- FORMULA SUPER-GAUSSIANA IMPIEGATA -------");
			IJ.log(curveFitter.getFormula());
			MyLog.logVector(initialGuessing, "initialGuessing");
			IJ.log("--------------------------------");
		}

		String results = curveFitter.getResultString();
		if (SPY)
		 {
			IJ.log(results);
//		double goodness = curveFitter.getFitGoodness();
//		int iterations = curveFitter.getIterations();
//		int restarts = curveFitter.getRestarts();
//		double[] residuals = curveFitter.getResiduals();
//		double RSquared = curveFitter.getRSquared();
		}

//		IJ.log("--------------------------------");
//		IJ.log(results);
//		IJ.log("goodness= " + goodness);
////		IJ.log("iterations= "+iterations);
//		IJ.log("restarts= " + restarts);
////		MyLog.logVector(residuals, "residuals");
////		IJ.log("RSquared= "+RSquared);
//		IJ.log("--------------------------------");

		double[] fitX = new double[len1];
		double[] fitY = new double[len1];

		for (int i1 = 0; i1 < len1; i1++) {
			fitX[i1] = i1;
			fitY[i1] = curveFitter.f(i1);
		}

		double[][] fitOut = ProfileUtils.encode(fitX, fitY);

//		double fitGoodness = curveFitter.getFitGoodness();
		String resultString = curveFitter.getResultString();

		double[] xpoints = curveFitter.getXPoints();
		double[] ypoints = curveFitter.getXPoints();
		int peakY = CurveFitter.getMax(ypoints);
		int peakX = 9999;
		for (int i1 = 0; i1 < xpoints.length; i1++) {
			if (ypoints[i1] == peakY) {
				peakX = i1;
				break;
			}
		}

		int xx = curveFitter.getNumParams();

		double[] params = curveFitter.getParams();
		double a1 = params[0];
		double b1 = params[1];
		double c1 = params[2];
		double d1 = params[3];
		double e1 = params[4];

		if (SPY) {
			MyLog.logVector(params, "supergaussian params");
		}

		double fwhmFormula = 2 * Math.sqrt(2) * d1 * Math.pow(Math.log(2), 1 / (2 * e1));
		// double FWHM = -d1;

		float dimPixel = ReadDicom
				.readFloat(ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 1));
		double sTeorico = ReadDicom.readFloat(
				ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_SLICE_THICKNESS), 1));

		// ===================================================================================
		// calcolo la FWHM anche col vecchio metodo ORIGINAL andando a cercare i pixels
		// sopra e sotto la mezza altezza con analplot e poi calcolando la FWHM con
		// calcFWHM
		// ===================================================================================

		int[] isd = analPlot1_FITTER(fitY, step, "");
		// MyLog.logVector(isd, "SUPERGAUSSIAN isd");
		double[] outfwhm1 = calcFwhm_FITTERpix(isd, fitY, slab, dimPixel, step);
		double fwhmConteggio = outfwhm1[0];

		if (step && SPY)
		 {
			MyLog.logVector(outfwhm1, "SUPERGAUSSIAN outFwhm");
		// MyLog.waitHere();
		// ===================================================================================
		}

		// ===================================================================================

		if (step) {
			spyname = "P038 - OUTPUT DI CURVEFITTER PER LA SUPER-GAUSSIANA";
			Plot plot1 = curveFitter.getPlot();
			plot1.setColor(Color.black);
			plot1.addLabel(0.01, 0.99, spyname);
			ImagePlus imp6 = plot1.show().getImagePlus();
			if (SPY) {
				p6rmn_COMMON.saveDebugImage_COMMON(imp6, spydir, spyname, contaxx++, SPY);
			}

			msgContinua("<72> - " + spyname);
			// MyLog.waitHere(spyname + " " + "@" + IJ.pad(contaxx++, 3));
			plot1.show();
		}

		if (verbose) {
			IJ.log("-----------------------------------------");
			IJ.log("----- ESEGUO IL FIT SUPER-GAUSSIAN ------");
			IJ.log("-----------------------------------------");
			IJ.log(resultString);
			IJ.log("-----------------------------------------");
		}
		// IJ.log("fitGoodness= " + fitGoodness);
		// IJ.log("FWHM [pix]= " + FWHM);
		// IJ.log("dimPixel= " + dimPixel);
		// IJ.log("corrAngolo 11.3= " + Math.sin(Math.toRadians(11.3)));

//		if (slab)
//			IJ.log("-------------- SUPER GAUSSIAN SLAB -----------------");
//		if (!slab)
//			IJ.log("-------------- SUPER GAUSSIAN WEDGE ----------------");
//		IJ.log("R^2= " + rsq + " FWHM [mm]= " + FWHM * dimPixel * Math.sin(Math.toRadians(11.3)));
//		IJ.log("-----------------------------------------------------");

		double[] outFWHM = new double[5];
		outFWHM[0] = fwhmConteggio;
		outFWHM[1] = fwhmFormula;
		outFWHM[2] = peakX;
		outFWHM[3] = peakY;
		outFWHM[4] = rsq;

		if (SPY) {
			spyname = "P039 - FWHM CALCOLATA SUL FIT SUPER-GAUSSIANO";
			Plot plot6 = p6rmn_COMMON.createPlot1_COMMON(fitOut, outFWHM, slab, true, false, spyname, sTeorico,
					dimPixel);
			ImagePlus imp6 = plot6.show().getImagePlus();
			if (!test) {
				p6rmn_COMMON.saveDebugImage_COMMON(imp6, spydir, spyname, contaxx++, SPY);
			}

			if (step)
			 {
				msgContinua("<73> - " + spyname);
			// MyLog.waitHere(spyname + " " + "@" + IJ.pad(contaxx++, 3));
			}
		}
//		if (rsq < 0.8) {
//			MyLog.waitHere("il fit supergaussiano teste' fatto fa superCAGARE!");
//		}

		return outFWHM;
	}

	/**
	 * calcolo ERF
	 *
	 * @param profile1 profilo da elaborare
	 * @param invert   true se da invertire
	 * @return profilo con ERF
	 */
	public double[][] createERF_FITTER(double[][] profile1, boolean step) {

		//
		// eseguo tre smooth 07-02-05, questo ci permette di ottenere risultati
		// "affidabili" non falsati da spikes
		//
		IJ.showStatus("createERF_FITTER");

		double[][] profile3 = profile1.clone();
		int len1 = profile3.length;

		double[][] profile4 = ProfileUtils.smooth(profile3);
		double[][] profile5 = ProfileUtils.smooth(profile4);
		double[][] profile6 = ProfileUtils.smooth(profile5);
		double[][] profile7 = ProfileUtils.smooth(profile6);
		double[][] profile8 = ProfileUtils.smooth(profile7);
		double[][] profile2 = ProfileUtils.smooth(profile8);

		// lavoro dopo un abbondante smooth per poter determinare bene il picco da
		// eliminare
		double[][] profileERF = edgeResponse_FITTER(profile2);

		if (step) {
			spyname = "P034 - PROFILO ERF CUNEO";
			Plot plot6 = MyPlot.plot1(profileERF, spyname);
			ImagePlus imp6 = plot6.show().getImagePlus();
			if (SPY) {
				p6rmn_COMMON.saveDebugImage_COMMON(imp6, spydir, spyname, contaxx++, SPY);
			}

			MyLog.waitHere(spyname);
		}
		//
		// decido di restare sulla strada precedente e cioe'di mettere a zero tutto il
		// picco massimo
		//
		double[] profileX = ProfileUtils.decodeX(profileERF);
		double[] profileY = ProfileUtils.decodeY(profileERF);
		double[] minMax = Tools.getMinMax(profileY);
		double min = minMax[0];
		double max = minMax[1];

		int peakX = 9999;
		double peakY = 9999;
		if ((Math.abs(min) > Math.abs(max)) && min < 0) {
//-----------------------
//			for (int i1 = 0; i1 < profileY.length; i1++) {
//				if (profileY[i1] < 0)
//					profileY[i1] = 0;
//			}
//-----------------------
			peakY = min;
			// CERCA POSIZIONE X PICCO NEGATIVO
			for (int i1 = 0; i1 < profileX.length; i1++) {
				if (profileY[i1] == min) {
					peakX = i1;
					break;
				}
			}
			// -------------------------------------------------
			// RIMOZIONE PICCO NEGATIVO
			// AL POSTO DEI VALORI DA ELIMINARE METTO -9999
			// --------------------------------------------------
			for (int i1 = peakX; i1 >= 0; i1--) {
				if (profileY[i1] < 0) {
					profileY[i1] = -9999;
				} else {
					break;
				}
			}
			for (int i1 = peakX + 1; i1 < profileY.length; i1++) {
				if (profileY[i1] < 0) {
					profileY[i1] = -9999;
				} else {
					break;
				}
			}
			// e poi cambio il tutto di segno, poiche' voglio il picco verso il
			// basso
			for (int i1 = 0; i1 < profileY.length; i1++) {
				profileY[i1] *= -1;
			}
		} else if ((Math.abs(max) > Math.abs(min)) && max > 0) {
//-----------------------
//			for (int i1 = 0; i1 < profileY.length; i1++) {
//				if (profileY[i1] > 0)
//					profileY[i1] = 0;
//			}
//-----------------------

			peakY = max;
			// CERCA POSIZIONE X PICCO POSITIVO
			for (int i1 = 0; i1 < profileX.length; i1++) {
				if (profileY[i1] == max) {
					peakX = i1;
					break;
				}
			}

			// se il massimo e'di valore assoluto piu'grande, allora porto a 0
			// tutti i valori maggiori di 0

			// -------------------------------------------------
			// RIMOZIONE PICCO POSITIVO
			// AL POSTO DEI VALORI DA ELIMINARE METTO 9999
			// --------------------------------------------------

			for (int i1 = peakX; i1 >= 0; i1--) {
				if (profileY[i1] > 0) {
					profileY[i1] = 9999;
				} else {
					break;
				}
			}
			for (int i1 = peakX + 1; i1 < profileY.length; i1++) {
				if (profileY[i1] > 0) {
					profileY[i1] = 9999;
				} else {
					break;
				}
			}

		}
		for (int i1 = 0; i1 < 6; i1++) {
			profileY[i1] = 9999;
		}
		for (int i1 = profileY.length - 1; i1 < profileY.length - 6; i1--) {
			profileY[i1] = 9999;
		}

		ArrayList<Double> arrProfileY2 = new ArrayList<Double>();
		ArrayList<Double> arrProfileX2 = new ArrayList<Double>();
		for (int i1 = 0; i1 < len1; i1++) {
			if (Math.abs(profileY[i1]) < 9900) {
				arrProfileY2.add(profileY[i1]);
				arrProfileX2.add((double) i1);
			}
		}
		double[] profiX = ArrayUtils.arrayListToArrayDouble(arrProfileX2);
		double[] profiY = ArrayUtils.arrayListToArrayDouble(arrProfileY2);

		double[][] erfOut2 = ProfileUtils.encode(profiX, profiY);

		return (erfOut2);
	}

	/**
	 * calcolo ERF
	 *
	 * @param profile1 profilo da elaborare
	 * @param invert   true se da invertire
	 * @return profilo con ERF
	 */
	public double[] createErf_ORIGINAL(double[] profile1, boolean invert) {

		// non utilizza altre routine

		int len1 = profile1.length;
		//
		// eseguo tre smooth 07-02-05, questo ci permette di ottenere risultati
		// affidabili non falsati da spikes
		//
		for (int j = 1; j < len1 - 1; j++) {
			profile1[j] = (profile1[j - 1] + profile1[j] + profile1[j + 1]) / 3;
		}
		for (int j = 1; j < len1 - 1; j++) {
			profile1[j] = (profile1[j - 1] + profile1[j] + profile1[j + 1]) / 3;
		}
		for (int j = 1; j < len1 - 1; j++) {
			profile1[j] = (profile1[j - 1] + profile1[j] + profile1[j + 1]) / 3;
		}
		for (int j = 1; j < len1 - 1; j++) {
			profile1[j] = (profile1[j - 1] + profile1[j] + profile1[j + 1]) / 3;
		}

		double[] erf = new double[len1];
		// if (invert) {
		for (int j = 0; j < profile1.length - 1; j++) {
			erf[j] = (profile1[j] - profile1[j + 1]) * (-1);
		}
		// } else {
		// for (int j = profile1.length - 1; j >= 0; j--)
		// erf[j] = (profile1[j] - profile1[j - 1]) * (-1);
		// }
		erf[len1 - 1] = erf[len1 - 2];

		// Anziche' utilizzare algoritmi di ricerca dei picchi, cerco il minimo
		// ed il massimo. Il valore assoluto piu' grande corrispondera' all'angolo
		// a 90 che non ci interessa. A questo punto posso portare a zero tutti
		// i valori del medesimo segno. Restera' cosi' solo il
		// picco meno alto, corrispondente all'erf della rampa del cuneo.

		double[] minMax = Tools.getMinMax(erf);
		double min = minMax[0];
		double max = minMax[1];

		if (Math.abs(min) > Math.abs(max) && min < 0) {
			// se il minimo e' di valore assoluto piu' grande, allora porto a 0
			// tutti i valori minori di 0
			for (int i1 = 0; i1 < erf.length; i1++) {
				if (erf[i1] < 0) {
					erf[i1] = 0;
				}
			}
			// e poi cambio il tutto di segno, poiche' voglio il picco verso il
			// basso
			for (int i1 = 0; i1 < erf.length; i1++) {
				erf[i1] *= -1;
			}

		} else if (Math.abs(min) < Math.abs(max) && max > 0) {
			// se il massimo e' di valore assoluto piu' grande, allora porto a 0
			// tutti i valori maggiori di 0
			for (int i1 = 0; i1 < erf.length; i1++) {
				if (erf[i1] > 0) {
					erf[i1] = 0;
				}
			}

		} else {
			MyLog.waitHere("QUESTA E'UNA STRANA ERF");
		}

		return (erf);
	} // createErf

	public double[][] edgeResponse_FITTER(double[][] profile1) {

		double[] profileX = ProfileUtils.decodeX(profile1);
		double[] profileY = ProfileUtils.decodeY(profile1);
		int len1 = profileY.length;
		double[] profileERF = new double[len1];
		for (int j = 0; j < len1 - 1; j++) {
			profileERF[j] = (profileY[j] - profileY[j + 1]) * (-1);
		}
		double[][] outERF = ProfileUtils.encode(profileX, profileERF);

		return outERF;
	}

//	/**
//	 * display del profilo fit con linea a meta' altezza
//	 *
//	 * @param bslab    true=slab false=cuneo
//	 * @param bLabelSx true=label a sx nel plot, false=label a dx
//	 * @param sTitolo  titolo del grafico
//	 * @param bFw      true=scritte x FWHM
//	 */
//	public Plot createPlot1(double[][] profile1, double[] fwhm, boolean bslab, boolean bFw, boolean bLabelSx,
//			String sTitolo, double spessoreTeorico, double dimPixel) {
//
//		double labPos;
//
//		double[] profileY = ProfileUtils.decodeY(profile1);
//		double[] a = Tools.getMinMax(profileY);
//
//		double min1 = a[0];
//		double max1 = a[1];
//		if (Math.abs(max1) > Math.abs(min1)) {
//			max1 = a[1];
//			min1 = a[0];
//		} else {
//			max1 = a[0];
//			min1 = a[1];
//		}
//
//		double half = (max1 - min1) / 2 + min1;
//
//		int len1 = profile1.length;
//		double[] xcoord1 = new double[len1];
//		for (int j = 0; j < len1; j++)
//			xcoord1[j] = j;
//
//		Plot plot = new Plot(sTitolo, "pixel", "valore", Plot.AUTO_POSITION);
//		plot.setColor(Color.black);
//		plot.addLabel(0.01, 0.99, sTitolo);
//		plot.setColor(Color.red);
//		plot.add("line", ProfileUtils.decodeX(profile1), ProfileUtils.decodeY(profile1));
//
//		if (bslab) {
////			plot.setLimits(0, len1, min1, max1);
////			plot.setLimits(0, len1, min1, Double.NaN);
//		} else {
////			plot.setLimits(0, len1, min1, 30);
////			plot.setLimits(0, len1, min1, Double.NaN);
//		}
//		plot.setLineWidth(1);
//
//		// linea a mezza altezza
//		plot.setColor(Color.green);
//		double[] pointX = new double[2];
//		double[] pointY = new double[2];
//		pointX[0] = 0;
//		pointX[1] = len1;
//		pointY[0] = half;
//		pointY[1] = half;
//		plot.addPoints(pointX, pointY, Plot.LINE);
//
//		plot.setColor(Color.black);
//		if (bLabelSx)
//			labPos = 0.10;
//		else
//			labPos = 0.60;
//		if (bFw) {
//			plot.addLabel(labPos, 0.75, "peak / 2 =   " + IJ.d2s(half, 2));
//			plot.addLabel(labPos, 0.80, "fwhm  [pixels]  =  " + IJ.d2s(fwhm[0], 2));
//			plot.addLabel(labPos, 0.85, "thick teorica   =  " + IJ.d2s(spessoreTeorico, 2));
//			double aux7 = fwhm[0] * dimPixel * Math.sin(Math.toRadians(11.3));
//			plot.addLabel(labPos, 0.90, "thick calcolata RAW =  " + IJ.d2s(aux7, 2));
//		}
//
//		return plot;
//
//	}

	/**
	 * display di un profilo con linea a meta' altezza
	 *
	 * @param profile1 profilo in ingresso
	 * @param bslab    true=slab false=cuneo
	 * @param bLabelSx true=label a sx nel plot, false=label a dx
	 * @param sTitolo  titolo del grafico
	 * @param bFw      true=scritte x FWHM
	 */
	public void createPlot2_ORIGINAL(double[] profile1, double dimPixel, boolean bslab, boolean bLabelSx,
			String sTitolo, boolean bFw) {
		int isd2[];
		double ddd[];
		double eee[];
		double fff[];
		double ggg[];
		double labPos;

		isd2 = analPlot1_ORIGINAL(profile1, bslab);

		double[] a = Tools.getMinMax(profile1);
		double min1 = a[0];
		double max1 = 0;
		if (bslab) {
			max1 = a[1];
		} else {
			max1 = 0;
		}

		double half = (max1 - min1) / 2 + min1;

		ddd = new double[2];
		eee = new double[2];
		fff = new double[2];
		ggg = new double[2];
		int len1 = profile1.length;
		double[] xcoord1 = new double[len1];
		for (int j = 0; j < len1; j++) {
			xcoord1[j] = j;
		}
		Plot plot = new Plot(sTitolo, "pixel", "valore");
		plot.setColor(Color.red);
		plot.addPoints(xcoord1, profile1, Plot.LINE);
		if (bslab) {
			plot.setLimits(0, len1, min1, max1);
		} else {
			plot.setLimits(0, len1, min1, 30);
		}
		plot.setLineWidth(1);
		plot.setColor(Color.blue);
		ddd[0] = isd2[0];
		ddd[1] = isd2[2];
		eee[0] = profile1[isd2[0]];
		eee[1] = profile1[isd2[2]];
		plot.addPoints(ddd, eee, PlotWindow.X);
		ddd[0] = isd2[1];
		ddd[1] = isd2[3];
		eee[0] = profile1[isd2[1]];
		eee[1] = profile1[isd2[3]];
		plot.addPoints(ddd, eee, Plot.CIRCLE);
		plot.changeFont(new Font("Helvetica", Font.PLAIN, 10));
		// interpolazione lineare sinistra
		double px0 = isd2[0];
		double px1 = isd2[1];
		double px2 = 0;
		double py0 = profile1[isd2[0]];
		double py1 = profile1[isd2[1]];
		double py2 = 0;
		py2 = half;
		px2 = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);
		double sx = px2;
		// interpolazione lineare destra
		px0 = isd2[2];
		px1 = isd2[3];
		py0 = profile1[isd2[2]];
		py1 = profile1[isd2[3]];
		py2 = half;
		px2 = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);
		double dx = px2;
		double fwhm = (dx - sx) * dimPixel;
		if (bLabelSx) {
			labPos = 0.10;
		} else {
			labPos = 0.60;
		}
		if (bFw) {
			plot.addLabel(labPos, 0.45, "peak / 2=   " + IJ.d2s(max1 / 2, 2));
			plot.addLabel(labPos, 0.50, "down sx " + isd2[0] + "  =   " + IJ.d2s(profile1[isd2[0]], 2));
			plot.addLabel(labPos, 0.55, "down dx " + isd2[2] + "  =   " + IJ.d2s(profile1[isd2[2]], 2));
			plot.addLabel(labPos, 0.60, "up      sx " + isd2[1] + "  =   " + IJ.d2s(profile1[isd2[1]], 2));
			plot.addLabel(labPos, 0.65, "up      dx " + isd2[3] + "  =   " + IJ.d2s(profile1[isd2[3]], 2));
			plot.addLabel(labPos, 0.70, "sx interp       =  " + IJ.d2s(sx, 2));
			plot.addLabel(labPos, 0.75, "dx interp       =  " + IJ.d2s(dx, 2));
			plot.addLabel(labPos, 0.80, "fwhm            =  " + IJ.d2s(fwhm, 2));
			plot.setColor(Color.green);
		}
		fff[0] = 0;
		fff[1] = len1;
		ggg[0] = half;
		ggg[1] = half;
		plot.addPoints(fff, ggg, Plot.LINE);
		plot.setColor(Color.red);
		plot.show();

		plot.draw();

	}

//	/**
//	 * un semplicissimo ma funzionale smoothing
//	 *
//	 * @param profile1 array su cui eseguire lo smoothing
//	 */
//	public void smooth(double[] profile1) {
//
//		for (int i1 = 1; i1 < profile1.length - 1; i1++)
//			profile1[i1] = (profile1[i1 - 1] + profile1[i1] + profile1[i1 + 1]) / 3.0;
//
//		return;
//	}

	/**
	 * SMOOTH
	 *
	 * @param profile1
	 */

	/**
	 * un semplicissimo ma funzionale smoothing
	 *
	 * @param profile1 array su cui eseguire lo smoothing
	 */
//	public void smooth2(double profile1[]) {
//
//		IJ.log("prima " + profile1[0] + " " + profile1[1] + " " + profile1[2] + " " + profile1[3] + " " + profile1[4]);
//		int len = profile1.length;
//		for (int i1 = 2; i1 < len - 2; i1++)
//			profile1[i1] = (profile1[i1 - 2] + profile1[i1 - 1] + profile1[i1] + profile1[i1 + 1] + profile1[i1 + 2])
//					/ 5.0;
//		profile1[0] = profile1[3];
//		profile1[1] = profile1[4];
//		profile1[len - 1] = profile1[len - 5];
//		profile1[len - 2] = profile1[len - 4];
//		IJ.log("dopo " + profile1[0] + " " + profile1[1] + " " + profile1[2] + " " + profile1[3] + " " + profile1[4]);
//		return;
//	}

//	public boolean myDoubleCompare(double uno, double due, double tolleranza) {
//
//		double diff = Math.abs(uno - due);
//		if (diff <= tolleranza)
//			return true;
//		else
//			return false;
//	}

	/**
	 * calcolo spessore di strato effettivo, apportando le correzioni per
	 * inclinazione e tilt (cio' che veniva effettuato dal foglio Excel)
	 *
	 * @param pixR1
	 * @param pixR2
	 * @param sTeor spessore teorico
	 * @return spessore dello strato
	 */
	public static double[] spessStrato(double pixR1, double pixR2, double sTeor, double dimPix) {

		// NOTA BENE CHE I VALORI: R1 ED R2 SONO ESPRESSI IN PIXELS
		// QUINDI LI RINOMINO COME pixR1 E pixR2
		// ergo lo stesso per S1 e S2 pixS1 e pixS2
		// ergo lo stesso per S1Cor ed S2Cor pixS1Cor e pixS2Cor
		// NOTA BENE CHE I VALORI S1Cor ED S2Cor SONO ESPRESSI IN PIXELS
		// QUINDI LI RINOMINO COME pixS1Cor e pixS2Cor

		double pixS1 = pixR1 * Math.tan(Math.toRadians(11));
		double pixS2 = pixR2 * Math.tan(Math.toRadians(11));
		double sen22 = Math.sin(Math.toRadians(22));
		double aux1 = -(pixS1 - pixS2) / (pixS1 + pixS2);
		double aux4 = Math.asin(sen22 * aux1);
		double tilt1Ramp = Math.toDegrees(0.5 * aux4);
		double aux2 = Math.tan(Math.toRadians(11.0 - tilt1Ramp));
		double aux3 = Math.tan(Math.toRadians(11.0 + tilt1Ramp));
		double pixS1Cor = aux3 * pixR1;
		double pixS2Cor = aux2 * pixR2;
		double accurSpess = 100.0 * (pixS1Cor * dimPix - sTeor) / sTeor;
		double erroreR1 = dimPix * aux3;
		double erroreR2 = dimPix * aux2;
		double erroreTot = Math.sqrt(erroreR1 * erroreR1 + erroreR2 * erroreR2);
		double erroreSper = 100.0 * erroreTot / sTeor;
		if (SPY) {
			IJ.log("------------------  SPESS_STRATO ----------------------");
			IJ.log("pixR1= " + pixR1 + " pixR2= " + pixR2);
			IJ.log("sen22= " + sen22);
			IJ.log("aux1= " + aux1);
			IJ.log("aux4= " + aux4);
			IJ.log("tilt1Ramp= " + tilt1Ramp);
			IJ.log("aux2= " + aux2 + " aux3= " + aux3);
			IJ.log("pixS1Cor= " + pixS1Cor + " pixS2Cor= " + pixS2Cor);
			IJ.log("mmS1Cor= " + pixS1Cor * dimPix + " mmS2Cor= " + pixS2Cor * dimPix);
		}

		double spessArray[] = new double[4];
		spessArray[0] = pixS1Cor;
		spessArray[1] = pixS2Cor;
		spessArray[2] = erroreSper;
		spessArray[3] = accurSpess;
		return spessArray;
	}

	/**
	 * La formula della correzione FWHM applicata e'ricavata dalla pubblicazione
	 * AAPM 100.
	 *
	 * PARE FUNZIONARE MOOOLTO MEGLIO
	 *
	 * @param a2
	 * @param b2
	 * @param dimPix
	 * @return
	 */

//	/**
//	 * impedisce che nelle preferenze di ImageJ vengano memorizzati segmenti con
//	 * valori assurdi, crea un area al di fuori della quale il valore memorizzato e'
//	 * quello di default, inoltre non permette che il segmento sia piu' corto di 10
//	 * pixel
//	 *
//	 * @param vetLine coordinate linea [ xStart, yStart, xEnd, yEnd ]
//	 * @param matrix  matrice immagine
//	 * @return true se interdetta memorizzazione
//	 */
//	public boolean interdiction(double[] vetLine, int matrix) {
//
//		if (vetLine[0] < 10)
//			return true;
//		if (vetLine[0] > (matrix - 10))
//			return true;
//		if (vetLine[2] < 10)
//			return true;
//		if (vetLine[2] > (matrix - 10))
//			return true;
//		double len = Math.sqrt(Math.pow(vetLine[0] - vetLine[2], 2) + Math.pow(vetLine[1] - vetLine[3], 2));
//		if (len < 10)
//			return true;
//		// se viene ribaltato il segmento interviene
//		if (vetLine[1] > vetLine[3])
//			return true;
//
//		return false;
//	}

//	/***
//	 * Impulso al fronte di salita
//	 *
//	 * @param input
//	 */
//	public static void stateChange(boolean input) {
//		pulse = false;
//		if ((input != previous) && !init1)
//			pulse = true;
//		init1 = false;
//		return;
//	}

	/**
	 * Restituisce i valori di riferimento per le immagini Siemens (e simili), i
	 * valori non dovrebbero mai cambiare, salvo che per modifiche a ImageJ
	 *
	 * @return
	 */
	public double[] referenceSiemens() {

		double slicePos = 10;
		double fwhmSlab1 = 10.090520971808136;
		double peak_slab1 = 42.96875;
		double fwhm_slab2 = 13.02010356505786;
		double peak_slab2 = 57.03125;
		double fwhm_cuneo3 = 8.090445406547264;
		double peak_cuneo3 = 44.53125;
		double fwhm_cuneo4 = 10.550518461420733;
		double peak_cuneo4 = 64.0625;
		double S1CorSlab = 2.211326690998348;
		double S2CorSlab = 2.211326690998348;
		double ErrSperSlab = 10.830367502157365;
		double AccurSpesSlab = 10.5663345499174;
		double S1CorCuneo = 1.7812946948464947;
		double S2CorCuneo = 1.7812946948464943;
		double ErrSperCuneo = 10.838079119386789;
		double AccurSpesCuneo = -10.935265257675264;
		double Accettab = 0.0;
		double DimPix = 0.78125;
		double Thick = 2.0;
		double Spacing = 0.0;
		double[] vetReference = { slicePos, fwhmSlab1, peak_slab1, fwhm_slab2, peak_slab2, fwhm_cuneo3, peak_cuneo3,
				fwhm_cuneo4, peak_cuneo4, S1CorSlab, S2CorSlab, ErrSperSlab, AccurSpesSlab, S1CorCuneo, S2CorCuneo,
				ErrSperCuneo, AccurSpesCuneo, Accettab, DimPix, Thick, Spacing };
		return vetReference;
	}

	/**
	 * Restituisce i valori di riferimento per le immagini Ge (e simili), i valori
	 * non dovrebbero mai cambiare, salvo che per modifiche a ImageJ
	 *
	 * @return
	 */
	public double[] referenceGe() {

		double slicePos = 1.71565;
		double fwhmSlab1 = 23.067207264738247;
		double peak_slab1 = 19.531500339508057;
		double fwhm_slab2 = 75.15392036949994;
		double peak_slab2 = 80.4697813987732;
		double fwhm_cuneo3 = 23.017457348952945;
		double peak_cuneo3 = 29.687880516052246;
		double fwhm_cuneo4 = 22.55055404276223;
		double peak_cuneo4 = 77.3447413444519;
		double S1CorSlab = 6.933279823491047;
		double S2CorSlab = 6.933279823491049;
		double ErrSperSlab = 4.9126882060578065;
		double AccurSpesSlab = 38.66559646982093;
		double S1CorCuneo = 4.428314036685271;
		double S2CorCuneo = 4.428314036685271;
		double ErrSperCuneo = 4.2955355260231265;
		double AccurSpesCuneo = -11.433719266294577;
		double Accettab = 0.0;
		double DimPix = 0.7812600135803223;
		double Thick = 5.0;
		double Spacing = 5.0;

		double[] vetReference = { slicePos, fwhmSlab1, peak_slab1, fwhm_slab2, peak_slab2, fwhm_cuneo3, peak_cuneo3,
				fwhm_cuneo4, peak_cuneo4, S1CorSlab, S2CorSlab, ErrSperSlab, AccurSpesSlab, S1CorCuneo, S2CorCuneo,
				ErrSperCuneo, AccurSpesCuneo, Accettab, DimPix, Thick, Spacing };
		return vetReference;
	}

	public static int msgSquare() {
		int userSelection = ButtonMessages.ModelessMsg("<01> - Far coincidere il segmento  con il lato sx del quadrato",
				"CONTINUA", "<ANNULLA>", 2, 1);
		return userSelection;
	}

	public static void msgSquareCoordinates(double[] vetReference) {
		ButtonMessages.ModelessMsg("<52> - coordinate posizionamento ax= " + vetReference[0] + "   ay = "
				+ vetReference[1] + "   bx = " + vetReference[2] + "  by = " + vetReference[3], "CONTINUA", 1, 1);
	}

	public static int msgAccept() {
		int userSelection = ButtonMessages.ModelessMsg("<53> - Accettabilita' immagine   <08>", "ELABORA", "SALTA", 1,
				1);
		return userSelection;
	}

	public static void msgProfile() {
		ButtonMessages.ModelessMsg("<54> - Analisi profilo e fwhm", "CONTINUA", 1, 1);
	}

	public static void msgWideline() {
		ButtonMessages.ModelessMsg("<55> - Profilo wideline", "CONTINUA", 1, 1);
	}

	public static void msgSlab() {
		ButtonMessages.ModelessMsg("<56> - Profilo mediato slab", "CONTINUA", 1, 1);
	}

	public static void msgBaseline() {
		ButtonMessages.ModelessMsg("<57> - Profilo mediato e baseline correction", "CONTINUA", 1, 1);
	}

	public static void msgFwhm() {
		ButtonMessages.ModelessMsg("<58> - Profilo mediato + baseline + FWHM", "CONTINUA", 1, 1);
	}

	public static void msgErf() {
		ButtonMessages.ModelessMsg("<59> - Profilo ERF + smooth 3x3 + FWHM", "CONTINUA", 1, 1);
	}

	public static void msgContinua(String msg) {
		ButtonMessages.ModelessMsg(msg, "CONTINUA", 1, 1);
	}

	public static String addPlus(String in1) {
		String out1 = "";
		if (in1.charAt(0) == '-') {
			out1 = in1;
		} else {
			out1 = "+" + in1;
		}
		return out1;
	}

}
