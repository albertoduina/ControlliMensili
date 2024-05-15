package contMensili;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.Line;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.io.OpenDialog;
import ij.measure.CurveFitter;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.measure.SplineFitter;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
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
 * Analizza SPESSORE FETTA effettua 4 profili, non necessariamente orizzontali ,
 * mediati su 11 linee e su questi calcola FWHM e posizione del picco. Lavora
 * sia su singola immagine che su stack
 * 
 * Per salvare i dati in formato xls necessita di Excel_Writer.jar nella
 * directory plugins
 * 
 * ____29jan07 v 3.00 i dati di output sugli spessori sono ora in mm (matr/fov)
 * 
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 * 
 */

public class p6rmn_FITTER implements PlugIn, Measurements {

	static final int ABORT = 1;

	public static final boolean SPY = true;
	// public static int preset=1;
	public static String spypath = "";
	public static String spyname = "";
	public static String spylog = "";
	public static String spyparent = "";
	public static String spyfirst = "";
	public static String spysecond = "";
	public static String spythird = "";
	public static int contaxx = 1;
	public static double ottusangolo = 0.0;

	public static String VERSION = "SPESSORE FETTA";

	private static String TYPE1 = " >> CONTROLLO THICKNESS______________";

	private static String TYPE2 = " >> CONTROLLO THICKNESS MULTISLICE___";

	private static String fileDir = "";

	private static boolean previous = false; // utilizzati per impulso
	private static boolean init1 = true; // utilizzati per impulso
	private static boolean pulse = false; // utilizzati per impulso

	// ---------------------------"01234567890123456789012345678901234567890"

	public void run(String args) {
		// for our phantom the nominal fwhm for thickness 2 mm is 10.3
		// for our phantom the nominal fwhm for thickness 5 mm is 25.7
		// peak position is the maximum profile position
		// manual selection of unaccettable slices (border or center (cross
		// slabs) slices

		UtilAyv.setMyPrecision();

		if (IJ.versionLessThan("1.53i"))
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
				if (path == null)
					return 5;
				boolean autoCalled = false;
				boolean verbose = true;
				boolean test = false;
				ImagePlus imp0 = UtilAyv.openImageNoDisplay(path[0], true);
				double[] oldPosition = readReferences(imp0);
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
		MyLog.appendLog(fileDir + "MyLog.txt", "p6 riceve " + autoArgs);

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
			if (nTokens == 1)
				// userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE1);
				userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE1,
						TableSequence.getCode(iw2ayvTable, vetRiga[0]), TableSequence.getCoil(iw2ayvTable, vetRiga[0]),
						vetRiga[0] + 1, TableSequence.getLength(iw2ayvTable));

			else
				// userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE2);
				userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE2,
						TableSequence.getCode(iw2ayvTable, vetRiga[0]), TableSequence.getCoil(iw2ayvTable, vetRiga[0]),
						vetRiga[0] + 1, TableSequence.getLength(iw2ayvTable));

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
				String[] path = loadPath(autoArgs);
				ImagePlus imp0 = UtilAyv.openImageNoDisplay(path[0], true);
				double[] vetRefPosition = readReferences(imp0);
				ResultsTable rt = null;
				boolean accetta = false;
				do {
					rt = mainThickness(path, autoArgs, vetRefPosition, autoCalled, step, verbose, test);
					if (rt != null) {
						// MyLog.here("verifyResultTable");
						rt.show("Results");
						if (autoCalled && !test) {
							if (SPY)
								accetta = true;
							else
								accetta = MyMsg.accettaMenu();
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
						int len = path.length;
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
				UtilAyv.saveResults(vetRiga, spyfirst + spysecond + "\\" + spythird, iw2ayvTable, rt);
				if (step)
					MyLog.waitHere();
				break;
			}
		} while (retry);
		new AboutBox().close();
		UtilAyv.afterWork();
		return 0;
	}

	public String[] manualImageSelection() {
		boolean endsel = false;
		List<String> listPath = new ArrayList<String>();
		do {
			OpenDialog od1 = new OpenDialog("SELEZIONARE IMMAGINE...", "");
			String directory1 = od1.getDirectory();
			String name1 = od1.getFileName();
			if (name1 == null)
				return null;
			listPath.add(directory1 + name1);
			int userSelection = ButtonMessages.ModelessMsg("Finito ?", "SELEZIONA ALTRA IMMAGINE", "FINE SELEZIONE");
			if (userSelection == 1)
				endsel = true;
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
				if (ok)
					MyMsg.msgTestPassed();
				else
					MyMsg.msgTestFault();
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
		if (ok)
			IJ.log("Il test di p6rmn_ THICKNESS e' stato SUPERATO");
		else
			IJ.log("Il test di p6rmn_ THICKNESS evidenzia degli ERRORI");
	}

	public String[] loadPath(String autoArgs) {
		String fileDir = Prefs.get("prefer.string1", "./test2/");
		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir + MyConst.SEQUENZE_FILE);
		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);
		String[] path = new String[vetRiga.length];
		for (int i1 = 0; i1 < vetRiga.length; i1++) {
			path[i1] = TableSequence.getPath(iw2ayvTable, vetRiga[i1]);
		}
		return path;
	}

	public double[] readReferences(ImagePlus imp1) {
		int rows = imp1.getHeight();
		float dimPixel = ReadDicom
				.readFloat(ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 1));

		double[] vetReference = new double[4];
		vetReference[0] = ReadDicom.readDouble(Prefs.get("prefer.p6rmnAx", "" + 50.0 / dimPixel));
		vetReference[1] = ReadDicom.readDouble(Prefs.get("prefer.p6rmnAy", "" + 60.0 / dimPixel));
		vetReference[2] = ReadDicom.readDouble(Prefs.get("prefer.p6rmnBx", "" + 50.0 / dimPixel));
		vetReference[3] = ReadDicom.readDouble(Prefs.get("prefer.p6rmnBy", "" + 215.0 / dimPixel));
		if (interdiction(vetReference, rows)) {
			vetReference[0] = 50.0 / dimPixel;
			vetReference[1] = 60.0 / dimPixel;
			vetReference[2] = 50.0 / dimPixel;
			vetReference[3] = 215.0 / dimPixel;
		}
		return vetReference;

	}

	public void saveReferences(ImagePlus imp1) {
		Line line1 = (Line) imp1.getRoi();
		Prefs.set("prefer.p6rmnAx", "" + line1.x1);
		Prefs.set("prefer.p6rmnAy", "" + line1.y1);
		Prefs.set("prefer.p6rmnBx", "" + line1.x2);
		Prefs.set("prefer.p6rmnBy", "" + line1.y2);
	}

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
		ResultsTable rt11 = null;
		boolean salta = false;
		// =============================================================================
		// =============================================================================
		// step = true;
		// =============================================================================
		// =============================================================================

		IJ.showStatus("mainThickness");

		ImagePlus impStack = stackBuilder(path, true);
		nFrames = path.length;

		impStack.setSliceWithoutUpdate(1);

		if (verbose)
			UtilAyv.showImageMaximized(impStack);
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

		if (SPY) {
			String aux1 = path[0];
			String aux2 = "";
			File f1 = new File(aux1);
			// --------------------------------------
			spyname = f1.getName();
			spythird = f1.getName();
			spyparent = f1.getParent();
			aux2 = spyparent;
			int pos1 = aux2.lastIndexOf(File.separatorChar);
			spyfirst = aux2.substring(0, pos1 + 1) + "STICAZZI";
			spysecond = aux2.substring(pos1);

			File theDir1 = new File(spyfirst);

			boolean via = Prefs.get("prefer.p6rmnSTART", false);
			Prefs.set("prefer.p6rmnSTART", false);

			if (via) {
				boolean ok1 = InputOutput.deleteDir(theDir1);
				if (step)
					MyLog.here("cancellata cartella " + spyfirst);

				if (!ok1 && step)
					MyLog.waitHere("errore cancellazione " + spyfirst);
			}
			boolean ok2 = InputOutput.createDirMultiple(spyfirst + spysecond + "\\");
			if (!ok2 && step)
				MyLog.waitHere("errore creazione " + spyfirst + spysecond + "\\");
			if (ok2 && step)
				MyLog.here("create cartelle " + spyfirst + spysecond + "\\");

			// File newdir = new File(spypath);
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
		ottusangolo = line1.getAngle();

		if (!test)
			saveReferences(impStack);
		if (step && !SPY)
			msgSquareCoordinates(vetRefPosition);
		saveDebugImage(impStack, spyfirst, spysecond, spyname);

		if (step)
			MyLog.here("dopo salvataggio prima immagine");

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

			if (verbose || SPY)
				UtilAyv.showImageMaximized(imp3);
			if (nFrames > 1) {
				int userSelection3 = msgAccept();
				if (userSelection3 == 1)
					salta = true;
				else
					salta = false;

				MyLog.waitHere("HAI SELEZIONATO " + userSelection3);

				vetAccettab[w1] = userSelection3;
			}

			if (!step)
				imp3.hide();
			// Phantom positioning: the phantom MUST have the slabs in high
			// position and the wedges in lower position
			//
			// First slab analysis
			//
			// refPosition [startX = 13, startY=65, endX = 147, endY=65,
			// radius=10]
			if (salta) {
			} else {

				if (step && !SPY)
					msgProfile();

				int ra1 = (int) (lato / 12.0);
				vetProfile[0] = lato / 13.0;
				vetProfile[1] = lato / 5.0;
				vetProfile[2] = lato - lato / 13.0;
				vetProfile[3] = lato / 5.0;

				boolean isSlab = true;
				boolean invertErf = false;
				boolean putLabelSx = false;
				if (imp3.isVisible()) {
					imp3.getWindow().toFront();
				}
				//
				// ##################################################################################################
				//
				double[] dsd1 = analProf(imp3, vetRefPosition, vetProfile, ra1, isSlab, invertErf, step, putLabelSx,
						dimPixel);
				//
				// ##################################################################################################
				//

				fwhmSlice1[w1] = dsd1[0];
				peakPositionSlice1[w1] = dsd1[1];

				if (imp3.isVisible())
					imp3.getWindow().toFront();
				//
				// Second slab analysis
				//
				if (step && !SPY)
					msgProfile();
				// refPosition [startX = 13, startY=65, endX = 147, endY=65,
				// radius=10]

				ra1 = (int) (lato / 12.0);
				vetProfile[0] = lato / 13.0;
				vetProfile[1] = lato * 2.0 / 5.0;
				vetProfile[2] = lato - lato / 13.0;
				vetProfile[3] = lato * 2.0 / 5.0;

				isSlab = true;
				invertErf = false;
				putLabelSx = true;
				if (step) {
					imp3.getWindow().toFront();
				}
				double[] dsd2 = analProf(imp3, vetRefPosition, vetProfile, ra1, isSlab, invertErf, step, putLabelSx,
						dimPixel);
				fwhmSlice2[w1] = dsd2[0];
				peakPositionSlice2[w1] = dsd2[1];

				if (imp3.isVisible())
					imp3.getWindow().toFront();
				double[] spessCor1 = spessStrato(dsd1[0], dsd2[0], (double) thick, dimPixel);

				vetS1CorSlab[w1] = spessCor1[0];
				vetS2CorSlab[w1] = spessCor1[1];
				vetErrSpessSlab[w1] = spessCor1[2];
				vetAccurSpessSlab[w1] = spessCor1[3];
				//
				// First wedge analysis
				//
				if (step && !SPY)
					msgProfile();
				// refPosition [startX = 13, startY=98, endX = 147, endY=98,
				// radius=10]

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

				double[] dsd3 = analProf(imp3, vetRefPosition, vetProfile, ra1, isSlab, invertErf, step, putLabelSx,
						dimPixel);

				fwhmCuneo3[w1] = dsd3[0];
				peakPositionCuneo3[w1] = dsd3[1];

				if (imp3.isVisible())
					imp3.getWindow().toFront();
				//
				// Second wedge analysis
				//
				if (step && !SPY)
					msgProfile();
				// refPosition [startX = 13, startY=133, endX = 147, endY=133,
				// radius=10]

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
				double[] dsd4 = analProf(imp3, vetRefPosition, vetProfile, ra1, isSlab, invertErf, step, putLabelSx,
						dimPixel);
				fwhmCuneo4[w1] = dsd4[0];
				peakPositionCuneo4[w1] = dsd4[1];

				if (imp3.isVisible())
					imp3.getWindow().toFront();
				double[] spessCor2 = spessStrato(dsd3[0], dsd4[0], (double) thick, dimPixel);
				vetS1CorCuneo[w1] = spessCor2[0];
				vetS2CorCuneo[w1] = spessCor2[1];
				vetErrSpessCuneo[w1] = spessCor2[2];
				vetAccurSpessCuneo[w1] = spessCor2[3];

			}
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
		for (int j1 = 0; j1 < nFrames; j1++)
			rt.addValue(s2 + j1, UtilAyv.convertToDouble(slicePos2[j1]));
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
		for (int j1 = 0; j1 < nFrames; j1++)
			rt.addValue(s2 + j1, vetErrSpessSlab[j1]);

		rt.incrementCounter();
		rt.addValue(t1, "AccurSpesSlab");
		for (int j1 = 0; j1 < nFrames; j1++)
			rt.addValue(s2 + j1, vetAccurSpessSlab[j1]);

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
		for (int j1 = 0; j1 < nFrames; j1++)
			rt.addValue(s2 + j1, vetErrSpessCuneo[j1]);

		rt.incrementCounter();
		rt.addValue(t1, "AccurSpesCuneo");
		for (int j1 = 0; j1 < nFrames; j1++)
			rt.addValue(s2 + j1, vetAccurSpessCuneo[j1]);

		rt.incrementCounter();
		rt.addValue(t1, "Accettab");
		for (int j1 = 0; j1 < nFrames; j1++)
			rt.addValue(s2 + j1, vetAccettab[j1]);

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

	public void saveDebugImage(ImagePlus imp1, String first, String second, String name) {
		ImagePlus impS3 = imp1.flatten();
		String newName = first + second + "\\" + name + "@" + contaxx++ + ".jpg";
		FileSaver fs = new FileSaver(impS3);
		// FileSaver.setJpegQuality(100);
		if (SPY)
			fs.saveAsJpeg(newName);
	}

	/**
	 * Costruisce uno stack a partire dal path delle immagini e lo riordina secondo
	 * la posizione delle fette.
	 * 
	 * @param path vettore contenente il path delle immagini
	 * @return ImagePlus contenente lo stack generato
	 */
	public ImagePlus stackBuilder(String[] path, boolean verbose) {

		if ((path == null) || (path.length == 0)) {
			if (verbose)
				IJ.log("stackBuilder path==null or 0 length.");
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
			if (w1 == 0)
				newStack.update(ip1);
			String sliceInfo1 = imp1.getTitle();
			String sliceInfo2 = (String) imp1.getProperty("Info");
			// aggiungo i dati header alle singole immagini dello stack
			if (sliceInfo2 != null)
				sliceInfo1 += "\n" + sliceInfo2;
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

		if (path == null)
			return null;
		if (slicePosition == null)
			return null;
		if (!(path.length == slicePosition.length))
			return null;
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

	public double[] analProf(ImagePlus imp1, double[] vetRefPosition, double[] vetProfile, int ra1, boolean slab,
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
		 * BASELINE CORRECTION
		 * 
		 * Aggiornamento del 08 maggio 2024 esperimento utilizzando il FIT di curve per
		 * la BASELINE CORRECTION, la definizione del picco e la FWHM
		 * 
		 * 
		 * ###########################################################################
		 * ###########################################################################
		 * ###########################################################################
		 * ###########################################################################
		 * ###########################################################################
		 */

		IJ.showStatus("analProf");

		if (imp1 == null)
			return null;

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
		double[][] profi1 = getLinePixels(imp1, c2x, c2y, d2x, d2y);
		Line.setWidth(1);

		if (SPY) {
			spyname = "001 - PROFILO MEDIATO SU 11 LINEE";
			Plot plot3 = MyPlot.plot1(profi1, spyname);
			ImagePlus imp3 = plot3.show().getPlot().getImagePlus();
			saveDebugImage(imp3, spyfirst, spysecond, spyname);
			if (step)
				MyLog.waitHere(spyname);
		}

		double[][] profi11 = profi1.clone();

		double[][] profi3 = baselineCorrectionNUOVO(imp1, profi11, step);

		double[][] profi9 = null;

		if (slab) {
			// caso della slab, mi limito a copiare i dati su profi9
			profi9 = profi3;
		} else {
			// caso del cuneo, calcolo la ERF tentando di fare la background correction
			// PRIMA di fare la ERF

			// MyLog.waitHere("ESAME CUNEO");
			double[][] profiERF = createERF(profi3, step); // profilo con ERF
			if (SPY) {
				spyname = "009 - PROFILO ERF CUNEO CON PICCO RIMOSSO";
				Plot plot3 = MyPlot.plot1(profiERF, spyname);
				ImagePlus imp3 = plot3.show().getPlot().getImagePlus();
				saveDebugImage(imp3, spyfirst, spysecond, spyname);
				if (step)
					MyLog.waitHere(spyname);
			}
			profi9 = profiERF;
		}
		//
		// da qui proseguo sia per slab che per cuneo
		//

		// ora che ho sottratto la baseline posso eseguire finalmente il fit del picco

		double[] outFWHM1 = peakFitterFWHM_GAUSSIAN(imp1, profi9, slab, step);
		double[] outFWHM2 = peakFitterFWHM_SUPERGAUSSIAN(imp1, profi9, slab, step);

		double sTeorico = (double) ReadDicom.readFloat(
				ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_SLICE_THICKNESS), 1));
		double dimPixel = (double) ReadDicom
				.readFloat(ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 1));
		//// =================================================================================
		//// NON MI PIACE AFFATTO IL CREATEPLOT2SPECIAL, FA DA SOLO UNA SECONDA VOLTA
		//// TUTTO IL RESTO, MA MI RASSEGNO, SIGH
		//// =================================================================================

//		int[] isd3 = analPlot1(profi13, slab);
//		double[] outFwhm = calcFwhmTraditional(isd3, profi13, slab, dimPixel);

		// MyLog.logVector(outFwhm, "outFwhm");

		if (step) {
			// ImagePlus imp5 = createPlot2(profiB1, true, bLabelSx, "Profilo mediato +
			// baseline correction", true);
			if (!SPY)
				msgBaseline();
			// saveDebugImage(imp5, spyfirst, spysecond, spyname);
		}

		return outFWHM1;
	}

	public double[][] profiTransform(double[] profi1) {

		double[][] out1 = new double[profi1.length][2];
		for (int i1 = 0; i1 < profi1.length; i1++) {
			out1[i1][0] = (double) i1;
			out1[i1][1] = (double) profi1[i1];
		}
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
	public double[] calcFwhmTraditional(int[] isd, double[] profile, boolean bslab, double dimPixel) {

		IJ.showStatus("calcFwhmTraditional");

		double peak = 0;
		double[] a = Tools.getMinMax(profile);
		double min1 = a[0];
		double max1 = 0;
		if (bslab)
			max1 = a[1];
		else
			max1 = 0;
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
		MyLog.waitHere("calcFwhmTraditional fwhm [mm]= " + fwhm * Math.sin(Math.toRadians(11.3)));

		for (int i1 = 0; i1 < profile.length; i1++) {
			if (profile[i1] == min1)
				peak = i1 * dimPixel;
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
	public double[][] getLinePixels(ImagePlus imp1, int x1, int y1, int x2, int y2) {

		// IJ.log("ricevo x1= " + x1 + " y1= " + y1 + " x2= " + x2 + " y2= " + y2);
		imp1.setRoi(new Line(x1, y1, x2, y2));
		imp1.updateAndDraw();
		Roi roi1 = imp1.getRoi();
		double[] profiM1 = ((Line) roi1).getPixels();
		double novusangolo = ((Line) roi1).getAngle();
		// IJ.log("Angolo ottenuto= " + novusangolo);
		double[][] out1 = ProfileUtils.encode(profiM1);
		return out1;
	}

	public double[] getLinePixels(ImagePlus imp1, double x1, double y1, double x2, double y2) {

		// IJ.log("ricevo x1= " + x1 + " y1= " + y1 + " x2= " + x2 + " y2= " + y2);
		imp1.setRoi(new Line(x1, y1, x2, y2));
		imp1.updateAndDraw();
		Roi roi1 = imp1.getRoi();
		double[] profiM1 = ((Line) roi1).getPixels();
		double novusangolo = ((Line) roi1).getAngle();
		// IJ.log("Angolo ottenuto= " + novusangolo + " gradi= " +
		// Math.toDegrees(novusangolo));
		return profiM1;
	}

	/**
	 * calcolo baseline correction del profilo assegnato
	 * 
	 * @param imp1     immagine di origine
	 * @param profile1 profilo da analizzare
	 * @param step     non usato !
	 * @return
	 */
	public double[][] baselineCorrectionNUOVO(ImagePlus imp1, double[][] profile1, boolean step) {

		IJ.showStatus("baselineCorrectionFITTER");

		int len1 = profile1.length;

		boolean[] local1 = localizzatore(profile1);

		double[] profileY = ProfileUtils.decodeY(profile1);

		ArrayList<Double> arrProfileY2 = new ArrayList<Double>();
		ArrayList<Double> arrProfileX2 = new ArrayList<Double>();
		for (int i1 = 0; i1 < len1; i1++) {
			if (local1[i1]) {
				arrProfileY2.add(profileY[i1]);
				arrProfileX2.add((double) i1);
			}
		}
		double[] profiX = ArrayUtils.arrayListToArrayDouble(arrProfileX2);
		double[] profiY = ArrayUtils.arrayListToArrayDouble(arrProfileY2);
		double[][] out2 = ProfileUtils.encode(profiX, profiY);

		if (step) {
			spyname = "002 - BASELINE RIMANENTE DOPO RIMOZIONE PICCO";
			Plot plot6 = MyPlot.plot1points(profile1, profiX, profiY, spyname);
			ImagePlus imp6 = plot6.show().getImagePlus();
			saveDebugImage(imp6, spyfirst, spysecond, spyname);
			if (step)
				MyLog.waitHere(spyname);
		}

		// ----------------------------------------------------------
		CurveFitter curveFitter = new CurveFitter(profiX, profiY);
		curveFitter.doFit(CurveFitter.POLY2);

		spyname = "002 - OUTPUT DI CURVEFITTER PER LA POLY2";
		if (SPY) {
			Plot plot1 = curveFitter.getPlot();
			plot1.setColor(Color.black);
			plot1.addLabel(0.01, 0.99, spyname);
			ImagePlus imp6 = plot1.show().getImagePlus();
			saveDebugImage(imp6, spyfirst, spysecond, spyname);
			if (step)
				MyLog.waitHere(spyname);
			plot1.show();
		}

		double[] fitY = new double[len1];
		double[] fitX = new double[len1];

		for (int i1 = 0; i1 < len1; i1++) {
			fitX[i1] = (double) i1;
			fitY[i1] = curveFitter.f((double) i1);
		}
		double[][] baselineFit = ProfileUtils.encode(fitX, fitY);

		if (SPY) {
			spyname = "003 - FIT BASELINE CON POLY2";
			Plot plot6 = MyPlot.plot2(profile1, baselineFit, spyname);
			ImagePlus imp6 = plot6.show().getImagePlus();

			saveDebugImage(imp6, spyfirst, spysecond, spyname);
			if (step)
				MyLog.waitHere(spyname);
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

		if (SPY) {
			spyname = "004 - PROFILO DOPO SOTTRAZIONE BASELINE";
			Plot plot6 = MyPlot.plot1(profileCorrect, spyname);
			ImagePlus imp6 = plot6.show().getImagePlus();
			saveDebugImage(imp6, spyfirst, spysecond, spyname);
			if (step)
				MyLog.waitHere(spyname);
		}

		return profileCorrect;
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

		IJ.showStatus("peakFitterFWHM_GAUSSIAN");

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
			fitX[i1] = (double) i1;
			fitY[i1] = curveFitter.f((double) i1);
		}
		double[][] fitOut = ProfileUtils.encode(fitX, fitY);

		double fitGoodness = curveFitter.getFitGoodness();
		String resultString = curveFitter.getResultString();
		String statusString = curveFitter.getStatusString();

		double[] xpoints = curveFitter.getXPoints();
		double[] ypoints = curveFitter.getXPoints();
		int peakY = curveFitter.getMax(ypoints);
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

		// da quanto ho capito la formula dell'FWHM del fit gaussiano e'data da
		//
		// FWHM= FWHM = 2 * Math.sqrt(2 * Math.log(2)) * SD
		//
		// FWHM circa= 2.3548 * SD

		// double FWHM = 2 * Math.sqrt(2 * Math.log(2)) * SD;

		// NON CAPISCO ERA GIUSTO QUELLO SOPRA !!!!
		// la devStand restituita da ImageJ era quella dell'errore e quindi ho trovato
		// un altra formula che utilizza il parametro c1 (vado ovviamente copiando senza
		// capirci na ceppa!)

		double FWHM = 2 * Math.sqrt(2 * Math.log(2)) * c1;

		float dimPixel = ReadDicom
				.readFloat(ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 1));
		double sTeorico = (double) ReadDicom.readFloat(
				ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_SLICE_THICKNESS), 1));

		// devo trasformare la FWHM da pixels su di un piano inclinato di 11.3 gradi a
		// millimetri di spessore
		//
		// FWHM [mm]= FWHM * dimPixel * Math.sin(Math.toRadians(11.3)
		//
		// per la immagine di test ho che il dimPixel=0.78125
		//

		spyname = "005 - OUTPUT DI CURVEFITTER PER LA GAUSSIANA";

		if (SPY) {
			Plot plot1 = curveFitter.getPlot();
			plot1.setColor(Color.black);
			plot1.addLabel(0.01, 0.99, spyname);
			ImagePlus imp6 = plot1.show().getImagePlus();
			saveDebugImage(imp6, spyfirst, spysecond, spyname);
			if (step)
				MyLog.waitHere(spyname);
			plot1.show();
		}

		IJ.log("----------------------------");
		IJ.log("resultString= " + resultString);
		IJ.log("----------------------------");
		IJ.log("fitGoodness= " + fitGoodness);
		IJ.log("FWHM [pix]= " + FWHM);
		IJ.log("dimPixel= " + dimPixel);
		IJ.log("corrAngolo 11.3= " + Math.sin(Math.toRadians(11.3)));
		IJ.log("FWHM [mm]= " + FWHM * dimPixel * Math.sin(Math.toRadians(11.3)));
		IJ.log("----------------------------");

		// MyLog.waitHere("Calcolo da fit gaussiana FWHM [mm]= " + FWHM * dimPixel *
		// Math.sin(Math.toRadians(11.3)));

		//
		// ----------------------------------------------------------
		// ----------------------------------------------------------
		// ----------------------------------------------------------
		// ----------------------------------------------------------

		double[] outFWHM = new double[3];
		outFWHM[0] = FWHM;
		outFWHM[1] = peakY;
		outFWHM[2] = peakX;

		if (SPY) {
			spyname = "006 - FWHM CALCOLATA SUL FIT GAUSSIANO";
			Plot plot6 = createPlot1(fitOut, outFWHM, slab, true, false, spyname, sTeorico, (double) dimPixel);
			ImagePlus imp6 = plot6.show().getImagePlus();
			saveDebugImage(imp6, spyfirst, spysecond, spyname);
			if (step)
				MyLog.waitHere(spyname);
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

		IJ.showStatus("peakFitterFWHM_SUPERGAUSSIAN");

		int len1 = profile1.length;
		double[] profiX = ProfileUtils.decodeX(profile1);
		double[] profiY = ProfileUtils.decodeY(profile1);

		String superGaussianFormula1 = "y = a + (b-a) * exp(-log(2)*(pow(4*(x-c)*(x-c)/ (d*d), e ) ) )";
		String superGaussianFormula2 = "y = a + b * exp(-1*(pow((x-c),e)/(2*pow(d, e ) ) ) )";
		String superGaussianFormula3 = "y= (b/(sqrt(2*pi)*d)) * pow(exp(-abs(a1-c1),e1) / (2* pow(d1,e1)))";
		String superGaussianFormula4 = " y = a + (b-a) exp(-(x-c)*(x-c)/(2*d*d))";

		// ----------------------------------------------------------
		//
		CurveFitter curveFitter = new CurveFitter(profiX, profiY);

		double[] initialGuessing = new double[5];
		initialGuessing[0] = -ArrayUtils.vetMin(profiY);
		initialGuessing[1] = -ArrayUtils.vetMax(profiY);
		initialGuessing[2] = -ArrayUtils.vetMean(profiY);
		initialGuessing[3] = -ArrayUtils.vetSd(profiY);
		initialGuessing[4] = -1.5;

		MyLog.logVector(initialGuessing, "initialGuessing");

		curveFitter.setRestarts(100);
		curveFitter.setMaxError(0.00001);

		boolean showSettings = false;
		curveFitter.doCustomFit(superGaussianFormula1, initialGuessing, showSettings);

		String results = curveFitter.getResultString();

		IJ.log(results);

		double[] fitX = new double[len1];
		double[] fitY = new double[len1];

		for (int i1 = 0; i1 < len1; i1++) {
			fitX[i1] = (double) i1;
			fitY[i1] = curveFitter.f((double) i1);
		}

		double[][] fitOut = ProfileUtils.encode(fitX, fitY);

		double fitGoodness = curveFitter.getFitGoodness();
		String resultString = curveFitter.getResultString();

		double[] xpoints = curveFitter.getXPoints();
		double[] ypoints = curveFitter.getXPoints();
		int peakY = curveFitter.getMax(ypoints);
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

		// double FWHM = 2 * Math.sqrt(2) * d1 * Math.pow(Math.log(2), 1 / (e1));
		double FWHM = -d1;
		
	

		float dimPixel = ReadDicom
				.readFloat(ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 1));
		double sTeorico = (double) ReadDicom.readFloat(
				ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_SLICE_THICKNESS), 1));

		spyname = "005 - OUTPUT DI CURVEFITTER PER LA SUPER-GAUSSIANA";

		if (SPY) {
			Plot plot1 = curveFitter.getPlot();
			plot1.setColor(Color.black);
			plot1.addLabel(0.01, 0.99, spyname);
			ImagePlus imp6 = plot1.show().getImagePlus();
			saveDebugImage(imp6, spyfirst, spysecond, spyname);
			if (step)
				MyLog.waitHere(spyname);
			plot1.show();
		}

		IJ.log("----------------------------");
		IJ.log("resultString= " + resultString);
		IJ.log("----------------------------");
		IJ.log("fitGoodness= " + fitGoodness);
		IJ.log("FWHM [pix]= " + FWHM);
		IJ.log("dimPixel= " + dimPixel);
		IJ.log("corrAngolo 11.3= " + Math.sin(Math.toRadians(11.3)));
		IJ.log("FWHM [mm]= " + FWHM * dimPixel * Math.sin(Math.toRadians(11.3)));
		IJ.log("----------------------------");

		double[] outFWHM = new double[3];
		outFWHM[0] = FWHM;
		outFWHM[1] = peakY;
		outFWHM[2] = peakX;

		if (SPY) {
			spyname = "006 - FWHM CALCOLATA SUL FIT SUPER-GAUSSIANO";
			Plot plot6 = createPlot1(fitOut, outFWHM, slab, true, false, spyname, sTeorico, (double) dimPixel);
			ImagePlus imp6 = plot6.show().getImagePlus();
			saveDebugImage(imp6, spyfirst, spysecond, spyname);
			if (step)
				MyLog.waitHere(spyname);
		}

		return outFWHM;
	}


	/**
	 * calcolo ERF
	 * 
	 * @param profile1 profilo da elaborare
	 * @param invert   true se da invertire
	 * @return profilo con ERF
	 */
	public double[][] createERF(double[][] profile1, boolean step) {

		//
		// eseguo tre smooth 07-02-05, questo ci permette di ottenere risultati
		// "affidabili" non falsati da spikes
		//
		IJ.showStatus("createErf");

		double[][] profile3 = profile1.clone();
		int len1 = profile3.length;

		double[][] profile4 = smooth(profile3);
		double[][] profile5 = smooth(profile4);
		double[][] profile6 = smooth(profile5);
		double[][] profile7 = smooth(profile6);
		double[][] profile8 = smooth(profile7);
		double[][] profile2 = smooth(profile8);

		// lavoro dopo un abbondante smooth per poter determinare bene il picco da
		// eliminare
		double[][] profileERF = edgeResponse(profile2);

		spyname = "008 - PROFILO ERF CUNEO";
		Plot plot6 = MyPlot.plot1(profileERF, spyname);
		ImagePlus imp6 = plot6.show().getImagePlus();
		saveDebugImage(imp6, spyfirst, spysecond, spyname);
		if (step)
			MyLog.waitHere(spyname);
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
				} else
					break;
			}
			for (int i1 = peakX + 1; i1 < profileY.length; i1++) {
				if (profileY[i1] < 0) {
					profileY[i1] = -9999;
				} else
					break;
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
				} else
					break;
			}
			for (int i1 = peakX + 1; i1 < profileY.length; i1++) {
				if (profileY[i1] > 0) {
					profileY[i1] = 9999;
				} else
					break;
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

	public double[][] edgeResponse(double[][] profile1) {

		double[] profileX = ProfileUtils.decodeX(profile1);
		double[] profileY = ProfileUtils.decodeY(profile1);
		int len1 = profileY.length;
		double[] profileERF = new double[len1];
		for (int j = 0; j < len1 - 1; j++)
			profileERF[j] = (profileY[j] - profileY[j + 1]) * (-1);
		double[][] outERF = ProfileUtils.encode(profileX, profileERF);

		return outERF;
	}

	/**
	 * display del profilo fit con linea a meta' altezza
	 * 
	 * @param bslab    true=slab false=cuneo
	 * @param bLabelSx true=label a sx nel plot, false=label a dx
	 * @param sTitolo  titolo del grafico
	 * @param bFw      true=scritte x FWHM
	 */
	public Plot createPlot1(double[][] profile1, double[] fwhm, boolean bslab, boolean bFw, boolean bLabelSx,
			String sTitolo, double spessoreTeorico, double dimPixel) {

		double labPos;

		double[] profileY = ProfileUtils.decodeY(profile1);
		double[] a = Tools.getMinMax(profileY);
		double min1 = a[0];
		double max1 = 0;
		if (bslab)
			max1 = a[1];
		else
			max1 = 0;

		double half = (max1 - min1) / 2 + min1;

		int len1 = profile1.length;
		double[] xcoord1 = new double[len1];
		for (int j = 0; j < len1; j++)
			xcoord1[j] = j;

		Plot plot = new Plot(sTitolo, "pixel", "valore", Plot.AUTO_POSITION);
		plot.setColor(Color.black);
		plot.addLabel(0.01, 0.99, sTitolo);
		plot.setColor(Color.red);
		plot.add("line", ProfileUtils.decodeX(profile1), ProfileUtils.decodeY(profile1));

		if (bslab) {
//			plot.setLimits(0, len1, min1, max1);
//			plot.setLimits(0, len1, min1, Double.NaN);
		} else {
//			plot.setLimits(0, len1, min1, 30);
//			plot.setLimits(0, len1, min1, Double.NaN);
		}
		plot.setLineWidth(1);

		// linea a mezza altezza
		plot.setColor(Color.green);
		double[] pointX = new double[2];
		double[] pointY = new double[2];
		pointX[0] = 0;
		pointX[1] = len1;
		pointY[0] = half;
		pointY[1] = half;
		plot.addPoints(pointX, pointY, Plot.LINE);

		plot.setColor(Color.black);
		if (bLabelSx)
			labPos = 0.10;
		else
			labPos = 0.60;
		if (bFw) {
			plot.addLabel(labPos, 0.75, "peak / 2 =   " + IJ.d2s(half, 2));
			plot.addLabel(labPos, 0.80, "fwhm  [pixels]  =  " + IJ.d2s(fwhm[0], 2));
			plot.addLabel(labPos, 0.85, "thick teorica   =  " + IJ.d2s(spessoreTeorico, 2));
			double aux7 = fwhm[0] * dimPixel * Math.sin(Math.toRadians(11.3));
			plot.addLabel(labPos, 0.90, "thick calcolata RAW =  " + IJ.d2s(aux7, 2));
		}

		return plot;

	}

	/**
	 * display di un profilo con linea a meta' altezza
	 * 
	 * @param profile1 profilo in ingresso
	 * @param bslab    true=slab false=cuneo
	 * @param bLabelSx true=label a sx nel plot, false=label a dx
	 * @param sTitolo  titolo del grafico
	 * @param bFw      true=scritte x FWHM
	 */
	public ImagePlus createPlot2special_FORSE_CANCELLABILE(double[] profile1, boolean bslab, boolean bLabelSx,
			String sTitolo, boolean bFw, double sTeorico, double dimPixel) {
		int isd2[];
		double ddd[];
		double eee[];
		double fff[];
		double ggg[];
		double labPos;

		isd2 = analPlot1_FORSE_CANCELLABILE(profile1, bslab);

		double[] a = Tools.getMinMax(profile1);
		double min1 = a[0];
		double max1 = 0;
		if (bslab)
			max1 = a[1];
		else
			max1 = 0;

		double half = (max1 - min1) / 2 + min1;

		ddd = new double[2];
		eee = new double[2];
		fff = new double[2];
		ggg = new double[2];
		int len1 = profile1.length;
		double[] xcoord1 = new double[len1];
		for (int j = 0; j < len1; j++)
			xcoord1[j] = j;
		Plot plot = new Plot(sTitolo, "pixel", "valore");
		plot.setColor(Color.black);
		plot.addLabel(0.01, 0.99, sTitolo);

		plot.setColor(Color.red);
		plot.add("line", xcoord1, profile1);
		if (bslab)
			plot.setLimits(0, len1, min1, max1);
		else
			plot.setLimits(0, len1, min1, 30);
		plot.setLineWidth(1);
		plot.setColor(Color.blue);
		ddd[0] = (double) isd2[0];
		ddd[1] = (double) isd2[2];
		eee[0] = profile1[isd2[0]];
		eee[1] = profile1[isd2[2]];
		plot.addPoints(ddd, eee, PlotWindow.X);
		ddd[0] = (double) isd2[1];
		ddd[1] = (double) isd2[3];
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
		double fwhm = dx - sx;
		if (bLabelSx)
			labPos = 0.10;
		else
			labPos = 0.60;

		double aux7 = fwhm * dimPixel * Math.sin(Math.toRadians(11.3));

		double aux1 = Math.abs(sTeorico - aux7);

		if (sTeorico == 2.0) {

			if (aux1 >= 0.8)
				MyLog.waitHere("ERRORE ECCESSIVO 2.0= " + aux7);
		}
		if (sTeorico == 5.0) {
			if (aux1 >= 1.0)
				MyLog.waitHere("ERRORE ECCESSIVO 5.0= " + aux7);

		}

//		double spessore = spessSimple(fwhm, sTeorico, dimPixel);
		if (bFw) {
			plot.addLabel(labPos, 0.45, "peak / 2.............= " + IJ.d2s(half, 2));
			plot.addLabel(labPos, 0.50,
					"down sx.............= [ " + isd2[0] + " ; " + IJ.d2s(profile1[isd2[0]], 2) + " ]");
			plot.addLabel(labPos, 0.55,
					"down dx............= [ " + isd2[2] + " ; " + IJ.d2s(profile1[isd2[2]], 2) + " ]");
			plot.addLabel(labPos, 0.60,
					"up sx.................= [ " + isd2[1] + " ; " + IJ.d2s(profile1[isd2[1]], 2) + " ]");
			plot.addLabel(labPos, 0.65,
					"up dx................= [ " + isd2[3] + " ; " + IJ.d2s(profile1[isd2[3]], 2) + " ]");
			plot.addLabel(labPos, 0.70, "sx interp............= " + IJ.d2s(sx, 2));
			plot.addLabel(labPos, 0.75, "dx interp...........= " + IJ.d2s(dx, 2));
			plot.addLabel(labPos, 0.80, "fwhm.................= " + IJ.d2s(fwhm, 2));
			plot.addLabel(labPos, 0.85, "thick teorica......= " + IJ.d2s(sTeorico, 2));
			plot.addLabel(labPos, 0.90, "thick calc.RAW.= " + IJ.d2s(aux7, 2));
			plot.setColor(Color.green);
		}
		fff[0] = 0;
		fff[1] = len1;
		ggg[0] = half;
		ggg[1] = half;
		// plot.addPoints(fff, ggg, PlotWindow.LINE);
		plot.addPoints(fff, ggg, Plot.LINE);
		plot.setColor(Color.red);
		plot.show();

//		plot.draw();
		return plot.getImagePlus();

	}

	/**
	 * analisi di un profilo normale con ricerca punti sopra e sotto mezza altezza
	 * 
	 * @param profile1 profilo da analizzare
	 * @param bSlab    true=slab false=cuneo
	 * @return isd[0] punto profilo sotto half a sx
	 * @return isd[1] punto profilo sopra half a sx
	 * @return isd[2] punto profilo sotto half a dx
	 * @return isd[3] punto profilo sopra half a dx
	 */
	public int[] analPlot1_FORSE_CANCELLABILE(double profile1[], boolean bSlab) {

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

		IJ.showStatus("analPlot1");

		double[] a = Tools.getMinMax(profile1);
		double min1 = a[0];
		if (bSlab)
			max1 = a[1];
		else
			max1 = 0;

		// calcolo mezza altezza
		double half = (max1 - min1) / 2 + min1;
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
		return isd;
	} // analPlot1

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
	public double[][] smooth(double[][] profile1) {

		double[] profileY = ProfileUtils.decodeY(profile1);
		for (int i1 = 1; i1 < profileY.length - 1; i1++)
			profileY[i1] = (profileY[i1 - 1] + profileY[i1] + profileY[i1 + 1]) / 3.0;
		double[][] profileOut = ProfileUtils.encode(ProfileUtils.decodeX(profile1), profileY);

		return profileOut;
	}

	/**
	 * un semplicissimo ma funzionale smoothing
	 * 
	 * @param profile1 array su cui eseguire lo smoothing
	 */
	public void smooth2(double profile1[]) {

		IJ.log("prima " + profile1[0] + " " + profile1[1] + " " + profile1[2] + " " + profile1[3] + " " + profile1[4]);
		int len = profile1.length;
		for (int i1 = 2; i1 < len - 2; i1++)
			profile1[i1] = (profile1[i1 - 2] + profile1[i1 - 1] + profile1[i1] + profile1[i1 + 1] + profile1[i1 + 2])
					/ 5.0;
		profile1[0] = profile1[3];
		profile1[1] = profile1[4];
		profile1[len - 1] = profile1[len - 5];
		profile1[len - 2] = profile1[len - 4];
		IJ.log("dopo " + profile1[0] + " " + profile1[1] + " " + profile1[2] + " " + profile1[3] + " " + profile1[4]);
		return;
	}

	public boolean myDoubleCompare(double uno, double due, double tolleranza) {

		double diff = Math.abs(uno - due);
		if (diff <= tolleranza)
			return true;
		else
			return false;
	}

	/**
	 * calcolo spessore di strato effettivo, apportando le correzioni per
	 * inclinazione e tilt (cio' che veniva effettuato dal foglio Excel)
	 * 
	 * @param R1
	 * @param R2
	 * @param sTeor spessore teorico
	 * @return spessore dello strato
	 */
	public double[] spessStrato(double R1, double R2, double sTeor, double dimPix2) {

		double S1 = R1 * Math.tan(Math.toRadians(11));
		double S2 = R2 * Math.tan(Math.toRadians(11));
		double Sen22 = Math.sin(Math.toRadians(22));
		double aux1 = -(S1 - S2) / (S1 + S2);
		double aux4 = Math.asin(Sen22 * aux1);
		double tilt1Ramp = Math.toDegrees(0.5 * aux4);
		double aux2 = Math.tan(Math.toRadians(11.3 - tilt1Ramp));
		double aux3 = Math.tan(Math.toRadians(11.3 + tilt1Ramp));
		double S1Cor = aux3 * R1;
		double S2Cor = aux2 * R2;
		double accurSpess = 100.0 * (S1Cor - sTeor) / sTeor;
		double erroreR1 = dimPix2 * aux3;
		double erroreR2 = dimPix2 * aux2;
		double erroreTot = Math.sqrt(erroreR1 * erroreR1 + erroreR2 * erroreR2);
		double erroreSper = 100.0 * erroreTot / sTeor;

		double spessArray[] = new double[4];
		spessArray[0] = S1Cor;
		spessArray[1] = S2Cor;
		spessArray[2] = erroreSper;
		spessArray[3] = accurSpess;

		return spessArray;
	}

	/**
	 * impedisce che nelle preferenze di ImageJ vengano memorizzati segmenti con
	 * valori assurdi, crea un area al di fuori della quale il valore memorizzato e'
	 * quello di default, inoltre non permette che il segmento sia piu' corto di 10
	 * pixel
	 * 
	 * @param vetLine coordinate linea [ xStart, yStart, xEnd, yEnd ]
	 * @param matrix  matrice immagine
	 * @return true se interdetta memorizzazione
	 */
	public boolean interdiction(double[] vetLine, int matrix) {

		if (vetLine[0] < 10)
			return true;
		if (vetLine[0] > (matrix - 10))
			return true;
		if (vetLine[2] < 10)
			return true;
		if (vetLine[2] > (matrix - 10))
			return true;
		double len = Math.sqrt(Math.pow(vetLine[0] - vetLine[2], 2) + Math.pow(vetLine[1] - vetLine[3], 2));
		if (len < 10)
			return true;
		// se viene ribaltato il segmento interviene
		if (vetLine[1] > vetLine[3])
			return true;

		return false;
	}

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
		int userSelection = ButtonMessages.ModelessMsg("Far coincidere il segmento  con il lato sx del quadrato",
				"CONTINUA", "<ANNULLA>", 2, 1);
		return userSelection;
	}

	public static void msgSquareCoordinates(double[] vetReference) {
		ButtonMessages.ModelessMsg("coordinate posizionamento ax= " + vetReference[0] + "   ay = " + vetReference[1]
				+ "   bx = " + vetReference[2] + "  by = " + vetReference[3], "CONTINUA", 1, 1);
	}

	public static int msgAccept() {
		int userSelection = ButtonMessages.ModelessMsg("Accettabilita' immagine   <08>", "ELABORA", "SALTA", 1, 1);
		return userSelection;
	}

	public static void msgProfile() {
		ButtonMessages.ModelessMsg("Analisi profilo e fwhm", "CONTINUA", 1, 1);
	}

	public static void msgWideline() {
		ButtonMessages.ModelessMsg("Profilo wideline", "CONTINUA", 1, 1);
	}

	public static void msgSlab() {
		ButtonMessages.ModelessMsg("Profilo mediato slab", "CONTINUA", 1, 1);
	}

	public static void msgBaseline() {
		ButtonMessages.ModelessMsg("Profilo mediato e baseline correction", "CONTINUA", 1, 1);
	}

	public static void msgFwhm() {
		ButtonMessages.ModelessMsg("Profilo mediato + baseline + FWHM", "CONTINUA", 1, 1);
	}

	public static void msgErf() {
		ButtonMessages.ModelessMsg("Profilo ERF + smooth 3x3 + FWHM", "CONTINUA", 1, 1);
	}

	/**
	 * Cerca di localizzare il profilo dell'oggetto, permettendo cosi' di rimuoverlo
	 * dal fono prima del fit. Faccio l'ipotesi di avere un unico oggetto, che il
	 * pixel minimo (lavoro in negativo) faccia parte dell'oggetto. Il profilo di
	 * cui dispongo e'di 132 pixel. Utilizzo una finestra detector di 5 pixel.
	 * Faccio anche l'ipotesi che ai lati dell'oggetto abbioamo il fondo, largo
	 * almeno 5 pixel.
	 * 
	 * @param profile1
	 */
	public static boolean[] localizzatore(double[][] profile1) {

		int len1 = profile1.length;
		// double[] profileX = ProfileUtils.decodeX(profile1);
		double[] profileY = ProfileUtils.decodeY(profile1);
		double[] minMax = Tools.getMinMax(profileY);
		double min = minMax[0];
		double max = minMax[1];
		double[] buffer = new double[5];
		boolean[] object1 = new boolean[len1];
		double mean1 = 0;
		double sd2 = ArrayUtils.vetSd(profileY);
		double transition = max - sd2;
		boolean isteresiUP = false;
		boolean isteresiDW = true;

		IJ.log("max= " + max + " min= " + min + " transition= " + transition);
		object1[0] = true;
		object1[1] = true;

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
		object1[len1 - 2] = false;
		object1[len1 - 1] = false;
		return object1;

	}

	/***
	 * Impulso al fronte di salita
	 * 
	 * @param input
	 */
	public static void stateChange(boolean input) {
		pulse = false;
		if ((input != previous) && !init1)
			pulse = true;
		init1 = false;
		return;
	}

}
