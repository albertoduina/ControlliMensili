package contMensili;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.Line;
import ij.gui.OvalRoi;
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
import ij.plugin.ScreenGrabber;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.util.Tools;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import utils.AboutBox;
import utils.ArrayUtils;
import utils.ButtonMessages;
import utils.ImageUtils;
import utils.InputOutput;
import utils.MyMsg;
import utils.MyConst;
import utils.MyGeometry;
import utils.MyLog;
import utils.MyStackUtils;
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

//SPLINE SPLINE SPLINE GNEGNEGNE

public class p6rmn_SPLINE1 implements PlugIn, Measurements {

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

				new p6rmn_SPLINE1().mainThickness(path, "0", oldPosition, autoCalled, step, verbose, test);
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
				ResultsTable rt = new p6rmn_SPLINE1().mainThickness(path1, autoArgs, vetRefPosition, autoCalled, step,
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

				ResultsTable rt = new p6rmn_SPLINE1().mainThickness(path1, autoArgs, vetRefPosition, autoCalled, step,
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

		ResultsTable rt = new p6rmn_SPLINE1().mainThickness(path1, autoArgs, vetRefPosition, autoCalled, step, verbose,
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

	@SuppressWarnings("deprecation")
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
		 * DATO IL NOME QUESTA SUBROUTINE NON PUO'CHE ESSERE UN PORNAZZO
		 * 
		 * Aggiornamento del 29 gennaio 2007 analProf da' in uscita i valori in
		 * millimetri, anziche' in pixel
		 * 
		 * Aggiornamento del 25 aprile 2024 esperimento utilizzando SPLINE per la
		 * BASELINE CORRECTION
		 * 
		 */

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
		double[] profi1 = getLinePixels(imp1, c2x, c2y, d2x, d2y);
		Line.setWidth(1);

		if (SPY) {
			spyname = "profilo su 11 linee";
			saveDebugImage(imp1, spyfirst, spysecond, spyname);
			// if (step)
			// MyLog.waitHere(spyname);
		}

		//// =================================================================================
		// 050524 provato a correggere il profilo prima di fare la ERF del cuneo, niente
		//// di bello!
		//// =================================================================================

		if (SPY) {
			spyname = "Profilo 11 linee";
			ImagePlus imp3 = plot1(profi1, spyname);
			saveDebugImage(imp3, spyfirst, spysecond, spyname);
			if (step)
				MyLog.waitHere(spyname);
		}

		double[] profi11 = profi1.clone();

		double[] profi9 = null;
		if (slab) {
			// caso della slab
			profi9 = profi11;

		} else {
			// caso del cuneo
			double[] profiE1 = createErf(profi11, invert); // profilo con ERF
			if (SPY) {
				spyname = "Funzione ERF per cuneo";
				ImagePlus imp3 = plot1(profiE1, spyname);
				saveDebugImage(imp3, spyfirst, spysecond, spyname);
				if (step)
					MyLog.waitHere(spyname);
			}
			profi9 = profiE1;
		}
		//
		// da qui proseguo sia per slab che per cuneo
		//
		double[] profi3 = baselineCorrectionFITTER(imp1, profi9, step);

		double[] profi13 = peakFITTER(imp1, profi3, step);

		double sTeorico = (double) ReadDicom.readFloat(
				ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_SLICE_THICKNESS), 1));
		double dimPixel = (double) ReadDicom
				.readFloat(ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 1));
		//// =================================================================================
		//// NON MI PIACE AFFATTO IL CREATEPLOT2SPECIAL, FA DA SOLO UNA SECONDA VOLTA
		//// TUTTO IL RESTO, MA MI RASSEGNO, SIGH
		//// =================================================================================

		int[] isd3 = analPlot1(profi13, slab);
		double[] outFwhm = calcFwhm(isd3, profi13, slab, dimPixel);

		// MyLog.logVector(outFwhm, "outFwhm");

		if (SPY) {
			spyname = "FWHM DEL PROFILO CORRETTO";
			ImagePlus imp7 = createPlot2special(profi13, true, false, spyname, true, sTeorico, dimPixel);
			saveDebugImage(imp7, spyfirst, spysecond, spyname);

			if (step)
				MyLog.waitHere(spyname);
		}
		if (step) {
			// ImagePlus imp5 = createPlot2(profiB1, true, bLabelSx, "Profilo mediato +
			// baseline correction", true);
			if (!SPY)
				msgBaseline();
			// saveDebugImage(imp5, spyfirst, spysecond, spyname);
		}

		return (outFwhm);
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

	public double[] analProf2(ImagePlus imp1, double[] vetRefPosition, double[] vetProfile, boolean slab,
			boolean invert, boolean step, boolean bLabelSx, double dimPixel, int linewidth) {

		/*
		 * Aggiornamento del 29 gennaio 2007 analProf da' in uscita i valori in
		 * millimetri, anziche' in pixel
		 */

		MyLog.waitHere("ANALPROF_2_");

		if (imp1 == null)
			return null;
		ImagePlus imp4 = null;
		double sTeorico = (double) ReadDicom.readFloat(
				ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_SLICE_THICKNESS), 1));

		// nota tecnica: quando si definisce una ROI con setRoi (ovale o
		// rettangolare che sia) passiamo a ImageJ le coordinate dell'angolo in
		// alto a Sx del BoundingRectangle per cui dobbiamo sempre includere nei
		// calcoli il raggio Roi

		IJ.log("inizio wideline slab");
		double[] msd1 = UtilAyv.coord2D2(vetRefPosition, vetProfile[0], vetProfile[1], true, ottusangolo);
		double c2x = msd1[0];
		double c2y = msd1[1];
		IJ.log("fine wideline slab");
		msd1 = UtilAyv.coord2D2(vetRefPosition, vetProfile[2], vetProfile[3], true, ottusangolo);
		double d2x = msd1[0];
		double d2y = msd1[1];
		//
		// linea calcolo segnale mediato oggetto
		//
		Line.setWidth(linewidth); ////////////////// 11
		double[] profiM2 = getLinePixels(imp1, c2x, c2y, d2x, d2y);
		saveDebugImage(imp1, spyfirst, spysecond, spyname);
		//
		// esperimento con la erf
		//

//		MyLog.waitHere();
//		imp4 = createPlot2(profiM2, true, bLabelSx, "Profilo mediato grezzo", false);
		imp4 = plot1(profiM2, "Profilo mediato grezzo nuovo");
		MyLog.waitHere("Profilo mediato grezzo ANALPROF_2_");

		// PROVIAMOCI

		double[] out1 = determinePoints(profiM2);

		float[] assex1 = new float[7];
		float[] assey1 = new float[7];

		assex1[0] = (float) out1[0];
		assex1[1] = (float) out1[2];
		assex1[2] = (float) out1[4];
		assex1[3] = (float) out1[6];
		assex1[4] = (float) out1[8];
		assex1[5] = (float) out1[10];
		assex1[6] = (float) out1[12];

		assey1[0] = (float) out1[1];
		assey1[1] = (float) out1[3];
		assey1[2] = (float) out1[5];
		assey1[3] = (float) out1[7];
		assey1[4] = (float) out1[9];
		assey1[5] = (float) out1[11];
		assey1[6] = (float) out1[13];

//		MyLog.logVector(assex1, "assex1");
//		MyLog.logVector(assey1, "assey1");
//		MyLog.waitHere();

		SplineFitter sf2 = new SplineFitter(assex1, assey1, assex1.length);

		double aa = 0;
		double[] spliney = new double[profiM2.length];
		for (int i1 = 0; i1 < profiM2.length; i1++) {
			aa = sf2.evalSpline(assex1, assey1, assex1.length, i1);
			spliney[i1] = aa;
		}

		ImagePlus imp5 = createPlot3(profiM2, spliney, assex1, assey1, true, false, "SPLINE sovrapposta a profilo",
				true, sTeorico, dimPixel);
		MyLog.waitHere("SPLINE sovrapposta a profilo");

//		MyLog.waitHere();
		smooth(profiM2);
//		imp4 = createPlot2(profiM2, true, bLabelSx, "SMOOTH 1", false);
//		MyLog.waitHere();
		smooth(profiM2);
//		imp4 = createPlot2(profiM2, true, bLabelSx, "SMOOTH 2", false);
//		MyLog.waitHere();
		smooth(profiM2);
//		imp4 = createPlot2(profiM2, true, bLabelSx, "SMOOTH 3", false);
//		MyLog.waitHere();

		double[] profiM1 = new double[profiM2.length];
		// if (invert) {
		for (int j = 0; j < profiM2.length - 1; j++)
			profiM1[j] = (profiM2[j] - profiM2[j + 1]) * (-1);
//		imp4 = createPlot2(profiM1, true, bLabelSx, "ERF 1", false);
//		MyLog.waitHere();

		smooth(profiM1);
		imp4 = createPlot2(profiM1, true, bLabelSx, "SMOOTH ERF1", false);
//		MyLog.waitHere();

		double[] profiM3 = new double[profiM1.length];
		// if (invert) {
		for (int j = 0; j < profiM3.length - 1; j++)
			profiM3[j] = (profiM1[j] - profiM1[j + 1]) * (-1);
		imp4 = createPlot2(profiM3, true, bLabelSx, "ERF 2", false);
		MyLog.waitHere();
		//
		//
		// ALGORITMO DELL'IMBECILLE CHE PERO'FUNZIONA (L'IMBECILLE, NON L'ALGORITMO)
		//
		//

		if (step) {
			imp1.updateAndDraw();
			if (!SPY)
				msgWideline();
			imp4 = createPlot2(profiM1, true, bLabelSx, "profilo ERF grezzo", false);
			saveDebugImage(imp4, spyfirst, spysecond, spyname);
			if (!SPY)
				msgSlab();

		}
		MyLog.waitHere();

		double[] profiB1 = profiM1;

		ImagePlus imp6 = createPlot2(profiB1, true, true, "Profilo DIFFERENZA", false);

		MyLog.waitHere();
		if (step) {
			// ImagePlus imp5 = createPlot2(profiB1, true, bLabelSx, "Profilo mediato +
			// baseline correction", true);
			if (!SPY)
				msgBaseline();
			// saveDebugImage(imp5, spyfirst, spysecond, spyname);
		}
		int isd3[];
		double[] outFwhm;
		if (slab) {
			isd3 = analPlot1(profiB1, slab);
			outFwhm = calcFwhm(isd3, profiB1, slab, dimPixel);
			if (step) {
				imp4 = createPlot2(profiB1, slab, bLabelSx, "plot mediato corretto baseline + FWHM", true);
				if (!SPY)
					msgFwhm();
				saveDebugImage(imp4, spyfirst, spysecond, spyname);

			}
			Line.setWidth(1);
			return (outFwhm);
		} else {
			double[] profiE1 = createErf(profiB1, invert); // profilo con ERF

			isd3 = analPlot1(profiE1, slab);

			outFwhm = calcFwhm(isd3, profiE1, slab, dimPixel);

			if (step) {
				imp4 = createPlot2(profiE1, slab, bLabelSx, "plot ERF con smooth 3x3 e FWHM", true);
				if (!SPY)
					msgErf();
				saveDebugImage(imp4, spyfirst, spysecond, spyname);

			}
			Line.setWidth(1);
			return (outFwhm);
		}
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
	public double[] calcFwhm(int[] isd, double[] profile, boolean bslab, double dimPixel) {

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
	public double[] getLinePixels(ImagePlus imp1, int x1, int y1, int x2, int y2) {

		// IJ.log("ricevo x1= " + x1 + " y1= " + y1 + " x2= " + x2 + " y2= " + y2);
		imp1.setRoi(new Line(x1, y1, x2, y2));
		imp1.updateAndDraw();
		Roi roi1 = imp1.getRoi();
		double[] profiM1 = ((Line) roi1).getPixels();
		double novusangolo = ((Line) roi1).getAngle();
		// IJ.log("Angolo ottenuto= " + novusangolo);
		return profiM1;
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
	 * calcolo spline del picco
	 * 
	 * @param profile1 profilo da correggere
	 * @param media1   media sul fondo a sx
	 * @param media2   media sul fondo a dx
	 * @return profilo corretto
	 */
	public double[] peakSPLINE(ImagePlus imp1, double[] profile1, double media1, double media2) {

		double sTeorico = (double) ReadDicom.readFloat(
				ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_SLICE_THICKNESS), 1));
		double dimPixel = (double) ReadDicom
				.readFloat(ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 1));

		double[] minMax = Tools.getMinMax(profile1);
		double min = minMax[0];
		double max = minMax[1];

		// adesso che ho il minimo faccio una media dei pixel in posizione 2,3,4, e
		// profile1.length-2, profile.length-3, profile.length-4. questa media la
		// sottraggo al max e poi divido per 2. Questa è la mia meta'altezza.
		int len1 = profile1.length;
		double base = (profile1[2] + profile1[3] + profile1[4] + profile1[len1 - 2] + profile1[len1 - 3]
				+ profile1[len1 - 4]) / 6;
		double half = min + (base - min) / 2;
		float[] vetx = new float[9];
		float[] vety = new float[9];
		double step1 = half / 10;
		int count = 0;
		boolean incrementa = false;

		IJ.log("base= " + base + "min= " + min + " half= " + half + " step1= " + step1);

		IJ.log("limiti impostati= " + half + " # " + (half - step1) + " # " + (half - 2 * step1) + " # "
				+ (half - 3 * step1) + " # " + min);

		// devo fare due ricerche che partono tutte e due dal punto minimo in una devo
		// andare all'indietro verso lo zero, nell'altra in avanti. Tutto cio' perche'
		// voglio che i punti siano sempre verso il basso.DEVO mettere i punti
		// in ordine altrimenti SPLINE mi sputa essendo un vero scugnizzo!
		// cerco la posizione X del minimo
		int xmin = -1;

		for (int i1 = 0; i1 < len1; i1++) {
			if (profile1[i1] == min) {
				xmin = i1;
				break;
			}
		}

//		MyLog.waitHere("xmin= " + xmin);

		// i miei punti saranno zero - uno - due -tre -quattro (minimo) - cinque -sei -
		// sette -otto
		count = 0;
		for (int i1 = xmin; i1 > 0; i1--) {
			if (profile1[i1] == (min) && count == 0) {
				vetx[4] = i1;
				vety[4] = (float) profile1[i1];
				incrementa = true;
			}
			if (profile1[i1] >= half - 3 * step1 && count == 1) {
				vetx[3] = i1;
				vety[3] = (float) profile1[i1];
				incrementa = true;
			}
			if (profile1[i1] >= half - 2 * step1 && count == 2) {
				vetx[2] = i1;
				vety[2] = (float) profile1[i1];
				incrementa = true;
			}
			if (profile1[i1] >= half - step1 && count == 3) {
				vetx[1] = i1;
				vety[1] = (float) profile1[i1];
				incrementa = true;
			}
			if (profile1[i1] >= half && count == 4) {
				vetx[0] = i1;
				vety[0] = (float) profile1[i1];
				incrementa = true;
			}
			if (incrementa) {
				count++;
				incrementa = false;
			}
		}

		/// sono arrivato qui ma mi sembra di avere scritto UNA CACATA PAZZESCA !!!!!

		count = 5;
		int aux2 = 0;
		for (int i1 = xmin; i1 < len1; i1++) {
//			MyLog.waitHere("at" + i1 + " -- " + profile1[i1] + " count= " + count);

			if (profile1[i1] >= half - 3 * step1 && count == 5) {
				vetx[5] = i1;
				vety[5] = (float) profile1[i1];
				incrementa = true;
			}
			if (profile1[i1] >= half - 2 * step1 && count == 6) {
				vetx[6] = i1;
				vety[6] = (float) profile1[i1];
				incrementa = true;
			}
			if (profile1[i1] >= half - step1 && count == 7) {
				vetx[7] = i1;
				vety[7] = (float) profile1[i1];
				incrementa = true;
			}
			if (profile1[i1] >= half && count == 8) {
				vetx[8] = i1;
				vety[8] = (float) profile1[i1];
				incrementa = true;
			}
			if (incrementa) {
				count++;
				incrementa = false;
			}
		}
		// MyLog.logVector(vetx, "vetx input SPLINE picco");
		// MyLog.logVector(vety, "vety input SPLINE picco");
		// MyLog.waitHere("vedi il vettore in IJ.log");

		SplineFitter sf1 = new SplineFitter(vetx, vety, vetx.length);

		double splinex[] = new double[len1];
		double spliney[] = new double[len1];

		double aux1 = 0;
		for (int i1 = 0; i1 < len1; i1++) {
			splinex[i1] = (double) i1;
			aux1 = sf1.evalSpline((double) i1);
			if (aux1 > 700.)
				aux1 = 700.;
			spliney[i1] = aux1;

		}

		// MyLog.logVectorVertical(spliney, "spliney output SPLINE picco");

		ImagePlus imp5 = createPlot3(profile1, spliney, vetx, vety, true, false, "SPLINE DEL PICCO", true, sTeorico,
				dimPixel);

		MyLog.waitHere();

		return spliney;
	}

	/**
	 * Analizza un profilo per delimitarne il picco
	 * 
	 * @param profile1
	 * @param step
	 * @return
	 */
	public int[] delimitaPicco(double[] profile1, boolean step) {

		double[] profile4smooth = smooth2(profile1);
		smooth(profile4smooth);
		smooth(profile4smooth);
		smooth(profile4smooth);

		if (SPY) {
			spyname = "Dopo SMOOTH";
			ImagePlus imp4 = plot2(profile1, profile4smooth, spyname);
			saveDebugImage(imp4, spyfirst, spysecond, spyname);
			if (step)
				MyLog.waitHere("Dopo SMOOTH");
		}

		double[] profile2 = derivataPrima(profile4smooth);
		spyname = "DERIVATA PRIMA";
//		ImagePlus imp15 = plot1(profile2, spyname);
		double[] profile6 = derivataSeconda(profile4smooth);
		spyname = "DERIVATA SECONDA";

		double[] minmax = Tools.getMinMax(profile2);

		int maxpos = 9999;
		int minpos = 9999;
		int sinistra = 9999;
		int destra = 9999;

		for (int i1 = 0; i1 < profile2.length; i1++) {
			if (profile2[i1] == minmax[0]) {
				minpos = i1;
			}
			if (profile2[i1] == minmax[1]) {
				maxpos = i1;
			}
		}

		if (minpos < maxpos) {
			// cerco lo zero della derivata seconda a sinistra del minimo
			for (int i1 = minpos - 1; i1 >= 0; i1--) {
				if (profile6[i1] >= 0) {
					sinistra = i1;
					break;
				}
			}
			for (int i1 = maxpos + 1; i1 < profile6.length; i1++) {
				if (profile6[i1] >= 0) {
					destra = i1;
					break;
				}
			}

		} else {
			MyLog.waitHere("HO LE CURVE AL CONTRARIO");
		}

		double[] spanx = new double[2];
		spanx[0] = (double) minpos;
		spanx[1] = (double) maxpos;

		double[] spany = new double[2];
		spany[0] = profile2[minpos];
		spany[1] = profile2[maxpos];

		/// MyLog.logVector(spany, "spany");

		if (SPY) {
			spyname = "DERIVATA PRIMA CON MIN MAX";
			spanx[0] = (double) minpos;
			spanx[1] = (double) maxpos;
			spany[0] = profile2[minpos];
			spany[1] = profile2[maxpos];
			ImagePlus imp5 = plot2points(profile2, spanx, spany, spyname);
			saveDebugImage(imp5, spyfirst, spysecond, spyname);
			if (step)
				MyLog.waitHere("DERIVATE PRIMA CON MIN MAX IN BLU");

			spyname = "DERIVATA SECONDA CON ZEROCROSSING";
			spanx[0] = (double) sinistra;
			spanx[1] = (double) destra;
			spany[0] = profile6[sinistra];
			spany[1] = profile6[destra];
			ImagePlus imp17 = plot2points(profile6, spanx, spany, spyname);
			saveDebugImage(imp5, spyfirst, spysecond, spyname);
			if (step)
				MyLog.waitHere("DERIVATA SECONDA CON ZERI IN BLU");
		}

		int[] out1 = new int[2];
		out1[0] = sinistra;
		out1[1] = destra;

		return out1;
	}

	/**
	 * calcolo baseline correction del profilo assegnato
	 * 
	 * @param imp1     immagine di origine
	 * @param profile1 profilo da analizzare
	 * @param step     non usato !
	 * @return
	 */
	public double[] baselineCorrectionSPLINE(ImagePlus imp1, double[] profile1, boolean step) {

		int len1 = profile1.length;

		int[] out1 = delimitaPicco(profile1, step);

		int sinistra = out1[0];
		int destra = out1[1];

		double[] profile4 = profile1;

		// --------------------------------------------
		// SCELTA DEI PUNTI SU CUI PASSA LA SPLINE
		// --------------------------------------------
		float assex[] = new float[6];
		float assey[] = new float[6];
		// inizio a sinistra
		assex[0] = (float) 5;
		assey[0] = (float) profile4[5];
		assex[1] = (float) sinistra / 2;
		assey[1] = (float) profile4[sinistra / 2];
		assex[2] = (float) sinistra;
		assey[2] = (float) profile4[sinistra];

		// passo a destra
		assex[3] = (float) destra;
		assey[3] = (float) profile4[destra];
		double aux3 = destra + 0.5 * (len1 - destra);

		assex[4] = (float) aux3;
		assey[4] = (float) profile4[(int) aux3];
		assex[5] = (float) len1 - 5;
		assey[5] = (float) profile4[len1 - 5];
//		MyLog.logVector(assex, "assex spline");
//		MyLog.logVector(assey, "assey spline");

		SplineFitter sf1 = new SplineFitter(assex, assey, assex.length);

		double splinex[] = new double[len1];
		double spliney[] = new double[len1];
		double aux1 = 0;
		for (int i1 = 0; i1 < len1; i1++) {
			splinex[i1] = (double) i1;
			aux1 = sf1.evalSpline((double) i1);
			spliney[i1] = aux1;
		}
		if (SPY) {
			spyname = "SPLINE";
			double[] assexx = UtilAyv.toDouble(assex);
			double[] asseyy = UtilAyv.toDouble(assey);
			ImagePlus imp6 = plot3points(profile1, spliney, assexx, asseyy, spyname);
			saveDebugImage(imp6, spyfirst, spysecond, spyname);
			if (step)
				MyLog.waitHere(spyname);
		}

		// la spline, pero' non va sottratta brutalmente (anche se andrebbe bene lo
		// stesso)

		double[] profile3 = new double[len1];
		for (int i1 = 0; i1 < len1; i1++) {
			profile3[i1] = profile1[i1] - spliney[i1];
		}

		if (SPY) {
			spyname = "PROFILO CORRETTO DOPO SPLINE";
			ImagePlus imp6 = plot1(profile3, spyname);
			saveDebugImage(imp6, spyfirst, spysecond, spyname);
			if (step)
				MyLog.waitHere(spyname);
		}

		return profile3;
	}

	/**
	 * calcolo baseline correction del profilo assegnato
	 * 
	 * @param imp1     immagine di origine
	 * @param profile1 profilo da analizzare
	 * @param step     non usato !
	 * @return
	 */
	public double[] baselineCorrectionFITTER(ImagePlus imp1, double[] profile1, boolean step) {

		int len1 = profile1.length;

		int[] out1 = delimitaPicco(profile1, step);

		int sinistra = out1[0];
		int destra = out1[1];

		// a questo punto devo rimuovere dal profilo tutti i punti tra sinistra e destra
		// del picco, utilizzo un ArrayList <PER SEMPLICITA'> :)

		ArrayList<Double> arrProfileY2 = new ArrayList<Double>();
		ArrayList<Double> arrProfileX2 = new ArrayList<Double>();
		for (int i1 = 0; i1 < profile1.length; i1++) {
			if (i1 < sinistra || i1 > destra) {
				arrProfileY2.add(profile1[i1]);
				arrProfileX2.add((double) i1);
			}
		}
		double[] profiX = ArrayUtils.arrayListToArrayDouble(arrProfileX2);
		double[] profiY = ArrayUtils.arrayListToArrayDouble(arrProfileY2);

		if (false) {
			spyname = "PROFILO X SENZA PICCO";
			ImagePlus imp6 = plot1(profiY, spyname);
			saveDebugImage(imp6, spyfirst, spysecond, spyname);
			if (step)
				MyLog.waitHere(spyname);
		}

		// ----------------------------------------------------------
		// ----------------------------------------------------------
		// ----------------------------------------------------------
		// ----------------------------------------------------------
		//
		CurveFitter curveFitter = new CurveFitter(profiX, profiY);
		curveFitter.doFit(CurveFitter.POLY4); // partiamo bassi !!!!

		double[] fitY = new double[len1];

		for (int i1 = 0; i1 < len1; i1++) {
			fitY[i1] = curveFitter.f((double) i1);
		}
		//
		// ----------------------------------------------------------
		// ----------------------------------------------------------
		// ----------------------------------------------------------
		// ----------------------------------------------------------

		if (SPY) {
			spyname = "BASELINE FITTER";
			ImagePlus imp6 = plot1(fitY, spyname);
			saveDebugImage(imp6, spyfirst, spysecond, spyname);
			if (step)
				MyLog.waitHere(spyname);
		}

		// la spline, pero' non va sottratta brutalmente (anche se andrebbe bene lo
		// stesso)

		double[] profile3 = new double[len1];
		for (int i1 = 0; i1 < len1; i1++) {
			profile3[i1] = profile1[i1] - fitY[i1];
		}

		if (SPY) {
			spyname = "PROFILE AFTER BASELINE FIT CORRECTION";
			ImagePlus imp6 = plot1(profile3, spyname);
			saveDebugImage(imp6, spyfirst, spysecond, spyname);
			if (step)
				MyLog.waitHere(spyname);
		}

		return profile3;
	}

	/**
	 * pulizia del picco utilizzando un FIT
	 * 
	 * @param imp1     immagine di origine
	 * @param profile1 profilo da analizzare
	 * @param step     non usato !
	 * @return
	 */
	public double[] peakFITTER(ImagePlus imp1, double[] profile1, boolean step) {

		int len1 = profile1.length;

		int[] out1 = delimitaPicco(profile1, step);

		int sinistra = out1[0];
		int destra = out1[1];
		if (step)
			MyLog.waitHere("sinistra= " + sinistra + " destra= " + destra);

		// a questo punto devo tenere solo i punti tra sinistra e destra
		// del picco, utilizzo un ArrayList <PER SEMPLICITA'> :)

		ArrayList<Double> arrProfileY2 = new ArrayList<Double>();
		ArrayList<Double> arrProfileX2 = new ArrayList<Double>();
		for (int i1 = 0; i1 < profile1.length; i1++) {
			if (i1 >= sinistra && i1 <= destra) {
				arrProfileY2.add(profile1[i1]);
				arrProfileX2.add((double) i1);
			}
		}
		double[] profiX = ArrayUtils.arrayListToArrayDouble(arrProfileX2);
		double[] profiY = ArrayUtils.arrayListToArrayDouble(arrProfileY2);

		if (SPY) {
			spyname = "PROFILO PICCO ISOLATO";
			ImagePlus imp6 = plot1(profiY, spyname);
			saveDebugImage(imp6, spyfirst, spysecond, spyname);
			if (step)
				MyLog.waitHere(spyname);
		}

		// ----------------------------------------------------------
		// ----------------------------------------------------------
		// ----------------------------------------------------------
		// ----------------------------------------------------------
		//
		CurveFitter curveFitter = new CurveFitter(profiX, profiY);
		curveFitter.doFit(CurveFitter.GAUSSIAN_NOOFFSET);

		double[] fitY = new double[len1];

		for (int i1 = 0; i1 < len1; i1++) {
			fitY[i1] = curveFitter.f((double) i1);
		}

		double fitGoodness = curveFitter.getFitGoodness();

		String formula = curveFitter.getFormula();

		String resultString = curveFitter.getResultString();
		double rSquared = curveFitter.getRSquared();
		String statusString = curveFitter.getStatusString();
		double SD = curveFitter.getSD();
		double[] params = curveFitter.getParams();
		double a1 = params[0];
		double b1 = params[1];
		double c1 = params[2];

		double FWHM = 2 * Math.sqrt(Math.log(2)) * c1;

		Plot plot1 = curveFitter.getPlot();
		plot1.show();

		IJ.log("fitGoodness= " + fitGoodness);
		IJ.log("formula= " + formula);
		IJ.log("resultString= " + resultString);
		IJ.log("rSquared= " + rSquared);
		IJ.log("statusString= " + statusString);
		IJ.log("fitGoodness= " + fitGoodness);
		IJ.log("SD= " + SD);
		IJ.log("a1= " + a1 + " b1= " + b1 + " c1= " + c1);

		IJ.log("FWHM= " + FWHM);
		IJ.log("FWHM [mm]= " + FWHM * 0.78125 * Math.sin(Math.toRadians(11.3)));
		MyLog.waitHere();

		//
		// ----------------------------------------------------------
		// ----------------------------------------------------------
		// ----------------------------------------------------------
		// ----------------------------------------------------------

		if (SPY) {
			spyname = "FIT DEL PICCO";
			ImagePlus imp6 = plot1(fitY, spyname);
			saveDebugImage(imp6, spyfirst, spysecond, spyname);
			if (step)
				MyLog.waitHere(spyname);
		}

		return fitY;
	}

	/**
	 * faccio la media di alcuni pixel (dispari) del profilo
	 * 
	 * @param profile1
	 * @param center
	 * @param width
	 * @return
	 */
	public float mediaY(double[] profile1, int center, int half) {
		double med = 0;
		for (int i1 = 0; i1 < (half * 2 + 1); i1++) {
			med = med + profile1[i1 + center];
		}
		return (float) med / (half * 2 + 1);
	}

	/**
	 * faccio la media di alcuni pixel (dispari) del profilo
	 * 
	 * @param profile1
	 * @param center
	 * @param width
	 * @return
	 */
	public float mediaY(double[] profile1, double center, int half) {
		double med = 0;
		for (int i1 = 0; i1 < (half * 2 + 1); i1++) {
			med = med + profile1[i1 + (int) Math.round(center)];
		}
		return (float) med / (half * 2 + 1);
	}

	/**
	 * calcolo ERF
	 * 
	 * @param profile1 profilo da elaborare
	 * @param invert   true se da invertire
	 * @return profilo con ERF
	 */
	public double[] createErf(double[] profile1, boolean invert) {

		int len1 = profile1.length;
		//

		// eseguo tre smooth 07-02-05, questo ci permette di ottenere risultati
		// "affidabili" non falsati da spikes
		//
		for (int j = 1; j < len1 - 1; j++)
			profile1[j] = (profile1[j - 1] + profile1[j] + profile1[j + 1]) / 3;
		for (int j = 1; j < len1 - 1; j++)
			profile1[j] = (profile1[j - 1] + profile1[j] + profile1[j + 1]) / 3;
		for (int j = 1; j < len1 - 1; j++)
			profile1[j] = (profile1[j - 1] + profile1[j] + profile1[j + 1]) / 3;
		for (int j = 1; j < len1 - 1; j++)
			profile1[j] = (profile1[j - 1] + profile1[j] + profile1[j + 1]) / 3;

		double[] erf = new double[len1];
		// if (invert) {
		for (int j = 0; j < profile1.length - 1; j++)
			erf[j] = (profile1[j] - profile1[j + 1]) * (-1);
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
				// ################################################
				if (erf[i1] < 0)
					erf[i1] = 0;
				// ################################################
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
				// ################################################
				if (erf[i1] > 0)
					erf[i1] = 0;
				// ################################################
			}

		} else
			MyLog.waitHere("QUESTA E'UNA STRANA ERF");

		return (erf);
	} // createErf

	/**
	 * display di un profilo con linea a meta' altezza
	 * 
	 * @param profile1 profilo in ingresso
	 * @param bslab    true=slab false=cuneo
	 * @param bLabelSx true=label a sx nel plot, false=label a dx
	 * @param sTitolo  titolo del grafico
	 * @param bFw      true=scritte x FWHM
	 */
	public ImagePlus createPlot2(double[] profile1, boolean bslab, boolean bLabelSx, String sTitolo, boolean bFw) {
		int isd2[];
		double ddd[];
		double eee[];
		double fff[];
		double ggg[];
		double labPos;

		isd2 = analPlot1(profile1, bslab);

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
		plot.addPoints(ddd, eee, Plot.DIAMOND);
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
		if (bFw) {
			plot.addLabel(labPos, 0.45, "peak / 2=   " + IJ.d2s(max1 / 2, 2));
			plot.addLabel(labPos, 0.50, "down sx " + isd2[0] + "  =   " + IJ.d2s(profile1[isd2[0]], 2));
			plot.addLabel(labPos, 0.55, "down dx " + isd2[2] + "  =   " + IJ.d2s(profile1[isd2[2]], 2));
			plot.addLabel(labPos, 0.60, "up      sx " + isd2[1] + "  =   " + IJ.d2s(profile1[isd2[1]], 2));
			plot.addLabel(labPos, 0.65, "up      dx " + isd2[3] + "  =   " + IJ.d2s(profile1[isd2[3]], 2));
			plot.addLabel(labPos, 0.70, "sx interp       =  " + IJ.d2s(sx, 2));
			plot.addLabel(labPos, 0.75, "dx interp       =  " + IJ.d2s(dx, 2));
			plot.addLabel(labPos, 0.80, "fwhm            =  " + IJ.d2s(fwhm, 2));
		}

//		plot.setColor(Color.green);
//		fff[0] = 0;
//		fff[1] = len1;
//		ggg[0] = half;
//		ggg[1] = half;
//		plot.addPoints(fff, ggg, Plot.LINE);
//		plot.setColor(Color.red);
		plot.show();

//		plot.draw();
		return plot.getImagePlus();

	}

	/**
	 * Plotta un profilo con linea a meta' altezza
	 * 
	 * @param profile1 profilo in ingresso
	 * @param bslab    true=slab false=cuneo
	 * @param bLabelSx true=label a sx nel plot, false=label a dx
	 * @param sTitolo  titolo del grafico
	 * @param bFw      true=scritte x FWHM
	 */
	public ImagePlus createPlot3(double[] profile1, double[] profile2, float[] assex, float[] assey, boolean bslab,
			boolean bLabelSx, String sTitolo, boolean bFw, double sTeorico, double dimPixel) {
		int isd2[];
		double ddd[];
		double eee[];
		double fff[];
		double ggg[];
		double labPos;

		MyLog.waitHere("entro in CERATE PLOT 3");

		isd2 = analPlot1(profile1, bslab);

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
//		Plot plot = new Plot(sTitolo, "pixel", "valore", xcoord1, profile1);
		Plot plot = new Plot(sTitolo, "pixel", "valore");
		plot.setColor(Color.black);
		plot.addLabel(0.01, 0.99, sTitolo);

		plot.setColor(Color.red);
		plot.add("line", xcoord1, profile1);
//		plot.setLineWidth(2);
//		plot.setColor(Color.green);
//		plot.add("circle", xcoord1, profile2);
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
		plot.setLineWidth(1);
		plot.addPoints(ddd, eee, Plot.CIRCLE);
		plot.setLineWidth(1);
		plot.changeFont(new Font("Helvetica", Font.PLAIN, 10));
		double[] assexx = new double[assex.length];
		double[] asseyy = new double[assey.length];
		for (int i1 = 0; i1 < assex.length; i1++) {
			assexx[i1] = (double) assex[i1];
			asseyy[i1] = (double) assey[i1];
		}

		MyLog.logVector(assexx, "assexx");
		MyLog.logVector(asseyy, "asseyy");
		MyLog.waitHere();

		plot.setLineWidth(3);
		plot.setColor(Color.red);
		plot.addPoints(assexx, asseyy, Plot.CIRCLE);
		plot.setLineWidth(1);

		MyLog.waitHere();
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
		if (bFw) {
			plot.addLabel(labPos, 0.45, "peak / 2=   " + IJ.d2s(max1 / 2, 2));
			plot.addLabel(labPos, 0.50, "down sx " + isd2[0] + "  =   " + IJ.d2s(profile1[isd2[0]], 2));
			plot.addLabel(labPos, 0.55, "down dx " + isd2[2] + "  =   " + IJ.d2s(profile1[isd2[2]], 2));
			plot.addLabel(labPos, 0.60, "up      sx " + isd2[1] + "  =   " + IJ.d2s(profile1[isd2[1]], 2));
			plot.addLabel(labPos, 0.65, "up      dx " + isd2[3] + "  =   " + IJ.d2s(profile1[isd2[3]], 2));
			plot.addLabel(labPos, 0.70, "sx interp       =  " + IJ.d2s(sx, 2));
			plot.addLabel(labPos, 0.75, "dx interp       =  " + IJ.d2s(dx, 2));
			plot.addLabel(labPos, 0.80, "fwhm            =  " + IJ.d2s(fwhm, 2));
			plot.addLabel(labPos, 0.85, "thick teorica   =  " + IJ.d2s(sTeorico, 2));
			double aux7 = fwhm * dimPixel * Math.sin(Math.toRadians(11.3));
			plot.addLabel(labPos, 0.90, "thick calc. RAW =  " + IJ.d2s(aux7, 2));
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
	 * Plotta un profilo con linea a meta' altezza
	 * 
	 * @param profile1 profilo in ingresso
	 * @param bslab    true=slab false=cuneo
	 * @param bLabelSx true=label a sx nel plot, false=label a dx
	 * @param sTitolo  titolo del grafico
	 * @param bFw      true=scritte x FWHM
	 */
	public ImagePlus createPlot2super(double[] profile1, double[] profile2, boolean bslab, boolean bLabelSx,
			String sTitolo, boolean bFw, double sTeorico, double dimPixel) {
		int isd2[];
		double ddd[];
		double eee[];
		double fff[];
		double ggg[];
		double labPos;

		MyLog.waitHere("entro in CREATE PLOT 2 SUPER");

		isd2 = analPlot1(profile1, bslab);

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
//		Plot plot = new Plot(sTitolo, "pixel", "valore", xcoord1, profile1);
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
		double aux7 = fwhm * dimPixel * Math.sin(Math.toRadians(11.3));

		if (bLabelSx)
			labPos = 0.10;
		else
			labPos = 0.60;
		if (bFw) {
			plot.addLabel(labPos, 0.45, "peak / 2=   " + IJ.d2s(max1 / 2, 2));
			plot.addLabel(labPos, 0.50, "down sx " + isd2[0] + "  =   " + IJ.d2s(profile1[isd2[0]], 2));
			plot.addLabel(labPos, 0.55, "down dx " + isd2[2] + "  =   " + IJ.d2s(profile1[isd2[2]], 2));
			plot.addLabel(labPos, 0.60, "up      sx " + isd2[1] + "  =   " + IJ.d2s(profile1[isd2[1]], 2));
			plot.addLabel(labPos, 0.65, "up      dx " + isd2[3] + "  =   " + IJ.d2s(profile1[isd2[3]], 2));
			plot.addLabel(labPos, 0.70, "sx interp       =  " + IJ.d2s(sx, 2));
			plot.addLabel(labPos, 0.75, "dx interp       =  " + IJ.d2s(dx, 2));
			plot.addLabel(labPos, 0.80, "fwhm            =  " + IJ.d2s(fwhm, 2));
			plot.addLabel(labPos, 0.85, "thick teorica   =  " + IJ.d2s(sTeorico, 2));
			plot.addLabel(labPos, 0.90, "thick calc. RAW =  " + IJ.d2s(aux7, 2));
			plot.setColor(Color.green);
		}
		fff[0] = 0;
		fff[1] = len1;
		ggg[0] = half;
		ggg[1] = half;
		// plot.addPoints(fff, ggg, PlotWindow.LINE);
//		plot.addPoints(fff, ggg, Plot.LINE);
		plot.setColor(Color.red);
		plot.show();

//		plot.draw();
		return plot.getImagePlus();

	}

	/**
	 * Plotta due profili
	 * 
	 * @param profile1
	 * @param profile2
	 * @param bslab
	 * @param bLabelSx
	 * @param sTitolo
	 * @param bFw
	 * @param sTeorico
	 * @param dimPixel
	 * @return
	 */

	public ImagePlus plot2(double[] profile1, double[] profile2, String sTitolo) {

		int len1 = profile1.length;
		double[] xcoord = new double[len1];
		for (int i1 = 0; i1 < len1; i1++) {
			xcoord[i1] = (double) i1;
		}

		Plot plot = new Plot(sTitolo, "pixel", "valore");
		plot.setColor(Color.black);
		plot.addLabel(0.01, 0.99, sTitolo);

		plot.setColor(Color.red);
		plot.add("line", xcoord, profile1);
		plot.setColor(Color.blue);
		plot.add("line", xcoord, profile2);
		plot.show();
		return plot.getImagePlus();
	}

	/**
	 * Plotta un profilo con punti sovrapposti
	 * 
	 * @param profile1
	 * @param xpoints2
	 * @param ypoints2
	 * @param sTitolo
	 * @return
	 */
	public ImagePlus plot2points(double[] profile1, double[] xpoints2, double[] ypoints2, String sTitolo) {

		int len1 = profile1.length;
		double[] xcoord = new double[len1];
		for (int i1 = 0; i1 < len1; i1++) {
			xcoord[i1] = (double) i1;
		}

		Plot plot = new Plot(sTitolo, "pixel", "valore");
		plot.setColor(Color.black);
		plot.addLabel(0.01, 0.99, sTitolo);

		plot.setColor(Color.red);
		plot.add("line", xcoord, profile1);
		plot.setLineWidth(2);
		plot.setColor(Color.blue);
		plot.addPoints(xpoints2, ypoints2, Plot.CIRCLE);
		plot.setLineWidth(1);

		plot.show();
		return plot.getImagePlus();
	}

	/**
	 * Plotta due profili con punti sovrapposti
	 * 
	 * @param profile1
	 * @param profile2
	 * @param xpoints2
	 * @param ypoints2
	 * @param sTitolo
	 * @return
	 */
	public ImagePlus plot3points(double[] profile1, double[] profile2, double[] xpoints2, double[] ypoints2,
			String sTitolo) {

		int len1 = profile1.length;
		double[] xcoord = new double[len1];
		for (int i1 = 0; i1 < len1; i1++) {
			xcoord[i1] = (double) i1;
		}

		Plot plot = new Plot(sTitolo, "pixel", "valore");
		plot.setColor(Color.black);
		plot.addLabel(0.01, 0.99, sTitolo);

		plot.setColor(Color.red);
		plot.add("line", xcoord, profile1);
		plot.setColor(Color.blue);
		plot.add("line", xcoord, profile2);

		plot.setLineWidth(2);
		plot.setColor(Color.blue);
		plot.addPoints(xpoints2, ypoints2, Plot.CIRCLE);
		plot.setLineWidth(1);

		plot.show();
		return plot.getImagePlus();
	}

	public ImagePlus plot1(double[] profile1, String sTitolo) {

		int len1 = profile1.length;
		double[] xcoord = new double[len1];
		for (int i1 = 0; i1 < len1; i1++) {
			xcoord[i1] = (double) i1;
		}

		Plot plot = new Plot(sTitolo, "pixel", "valore");
		plot.setColor(Color.black);
		plot.addLabel(0.01, 0.99, sTitolo);

		plot.setColor(Color.red);
		plot.add("line", xcoord, profile1);
		plot.show();
		return plot.getImagePlus();

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
	public ImagePlus createPlot2special(double[] profile1, boolean bslab, boolean bLabelSx, String sTitolo, boolean bFw,
			double sTeorico, double dimPixel) {
		int isd2[];
		double ddd[];
		double eee[];
		double fff[];
		double ggg[];
		double labPos;

		isd2 = analPlot1(profile1, bslab);

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
	public int[] analPlot1(double profile1[], boolean bSlab) {

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

	/**
	 * un semplicissimo ma funzionale smoothing
	 * 
	 * @param profile1 array su cui eseguire lo smoothing
	 */
	public void smooth(double profile1[]) {

		for (int i1 = 1; i1 < profile1.length - 1; i1++)
			profile1[i1] = (profile1[i1 - 1] + profile1[i1] + profile1[i1 + 1]) / 3.0;

		return;
	}

	/**
	 * un semplicissimo ma funzionale smoothing
	 * 
	 * @param profile1 array su cui eseguire lo smoothing
	 */
	public double[] smooth2(double profile1[]) {

		double profile2[] = new double[profile1.length];
		for (int i1 = 1; i1 < profile1.length - 1; i1++)
			profile2[i1] = (profile1[i1 - 1] + profile1[i1] + profile1[i1 + 1]) / 3.0;
		profile2[0] = profile2[1];
		profile2[profile2.length - 1] = profile2[profile2.length - 2];
		return profile2;
	}

	/**
	 * calcolo della derivata prima fatto MALE
	 * 
	 * @param profile1
	 * @return
	 */
	public double[] derivataPrima(double profile1[]) {

		double[] profile2 = new double[profile1.length];
		for (int i1 = 1; i1 < profile1.length - 1; i1++)
			profile2[i1] = -0.5 * profile1[i1 - 1] + 0.5 * profile1[i1 + 1];
		return profile2;
	}

	/**
	 * calcolo della derivata seconda fatto MALE
	 * 
	 * @param profile1 array su cui eseguire l'operazione
	 */
	public double[] derivataSeconda(double profile1[]) {

		double[] profile2 = new double[profile1.length];
		for (int i1 = 1; i1 < profile1.length - 1; i1++)
			profile2[i1] = 1 * profile1[i1 - 1] - 2 * profile1[i1] + 1 * profile1[i1 + 1];
		return profile2;
	}

	/**
	 * ricerca degli zero crossing dopo la derivata
	 * 
	 * @param profile1 array su cui eseguire l'operazione
	 */
	public int[] derivateZeroCrossing(double profile1[]) {

		double max = Double.MIN_VALUE;
		int maxpos = 0;
		double min = Double.MAX_VALUE;
		int minpos = 0;
		int zeroRight = 0;
		int zeroLeft = 0;

		for (int i1 = 1; i1 < profile1.length; i1++) {
			if (profile1[i1] > max) {
				max = profile1[i1];
				maxpos = i1;
			}
			if (profile1[i1] < min) {
				min = profile1[i1];
				minpos = i1;
			}
		}
		if (maxpos > minpos) {
			for (int i1 = maxpos; i1 < profile1.length; i1++) {
				if (profile1[i1] <= 0) {
					zeroRight = i1;
					break;
				}
			}
			for (int i1 = minpos; i1 > 0; i1--) {
				if (profile1[i1] >= 0) {
					zeroLeft = i1;
					break;
				}
			}
		} else {
			MyLog.waitHere("HOUSTON ABBIAMO UN PROBLEMA");
		}

		int[] zeri = new int[2];
		zeri[0] = zeroLeft;
		zeri[1] = zeroRight;

		return zeri;
	}

	/**
	 * In questa versione cerco la distanza tra massimo e minimo in x della
	 * derivata. Poi, basandomi sullo zero compreso tra minimo e massimo applico la
	 * distanza sia a dx che sx e assumo che la larghezza della slab sia questa. Al
	 * di fuori posso prendere i punti su cui poi fare la spline. NON SO SE HA
	 * SENSO, MA CI PROVO
	 * 
	 * @param profile1
	 * @param gradino
	 * @return
	 */

	public double[] derivateSpan(double profile1[]) {

		double max = Double.MIN_VALUE;
		int maxpos = 0;
		double min = Double.MAX_VALUE;
		int minpos = 0;
//		int zeropoint = 0;

		spyname = "DEBUG DERIVATA";
		ImagePlus imp4 = plot1(profile1, spyname);
		saveDebugImage(imp4, spyfirst, spysecond, spyname);

		for (int i1 = 1; i1 < profile1.length; i1++) {
			if (profile1[i1] > max) {
				max = profile1[i1];
				maxpos = i1;
			}
			if (profile1[i1] < min) {
				min = profile1[i1];
				minpos = i1;
			}
		}

		double span = maxpos - minpos;

//		if (maxpos > minpos) {
//			for (int i1 = minpos; i1 < maxpos; i1++) {
//
//				//
//				// vado a cercare lo zero sempre presente tra min e max
//				//
//
//				if (profile1[i1] <= 0) {
//					zeropoint = i1;
//					break;
//				}
//
//			}
//		}

		double leftpoint = minpos - 0.5 * span;
		double rightpoint = maxpos + 0.5 * span;
		double[] out1 = new double[2];
		out1[0] = leftpoint;
		out1[1] = rightpoint;
		IJ.log("maxpos= " + maxpos);
		IJ.log("minpos= " + minpos);
		IJ.log("span= " + span);
		IJ.log("leftpoint= " + leftpoint);
		IJ.log("rightpoint= " + rightpoint);
		return out1;
	}

	/**
	 * ricerca degli incroci della derivata prima con lo zero oppure col gradino
	 * (inteso come percentuale di max e min)
	 * 
	 * @param profile1 profilo derivata prima
	 * @param gradino  gradino percentuale max e min
	 * @return
	 */
	public double[][] derivateZeroCrossing(double profile1[], double gradino) {

		double max = Double.MIN_VALUE;
		int maxpos = 0;
		double min = Double.MAX_VALUE;
		int minpos = 0;
		int zeroRight = 0;
		int pre1Right = 0;
		int pre2Right = 0;
		int zeroLeft = 0;
		int pre1Left = 0;
		int pre2Left = 0;
		boolean trovatoR0 = false;
		boolean trovatoR1 = false;
		boolean trovatoR2 = false;
		boolean trovatoL0 = false;
		boolean trovatoL1 = false;
		boolean trovatoL2 = false;

		for (int i1 = 1; i1 < profile1.length; i1++) {
			if (profile1[i1] > max) {
				max = profile1[i1];
				maxpos = i1;
			}
			if (profile1[i1] < min) {
				min = profile1[i1];
				minpos = i1;
			}
		}

		double pip1 = gradino * max;
		double pip2 = 1.2 * pip1;

		if (maxpos > minpos) {
			for (int i1 = maxpos; i1 < profile1.length; i1++) {

				//
				// oltre allo zero trovo anche due gradini di cui scopriremo in seguito
				// l'utilita', magari invece sono inutili
				//

				if ((profile1[i1] <= pip2) && (!trovatoR2)) {
					pre2Right = i1;
					trovatoR2 = true;
					IJ.log("punto pip2R x= " + i1 + " y= " + pip2);
				}

				if ((profile1[i1] <= pip1) && (!trovatoR1)) {
					pre1Right = i1;
					trovatoR1 = true;
					IJ.log("punto pip1R x= " + i1 + " y= " + pip1);
				}
				if ((profile1[i1] <= 0) && (!trovatoR0)) {
					zeroRight = i1;
					trovatoR0 = true;
					IJ.log("punto zeroR x= " + i1 + " y= 0");
					break;
				}

			}
			for (int i1 = minpos; i1 > 0; i1--) {
				if ((profile1[i1] >= -pip2) && (!trovatoL2)) {
					pre2Left = i1;
					trovatoL2 = true;
					IJ.log("punto pip2L x= " + i1 + " y= " + (-pip2));
				}

				if ((profile1[i1] >= -pip1) && (!trovatoL1)) {
					pre1Left = i1;
					trovatoL1 = true;
					IJ.log("punto pip1L x= " + i1 + " y= " + (-pip1));
				}
				if ((profile1[i1] >= 0) && (!trovatoL0)) {
					zeroLeft = i1;
					trovatoL0 = true;
					IJ.log("punto zeroL x= " + i1 + " y= 0");
					break;
				}
			}
		} else {
			MyLog.waitHere("HOUSTON ABBIAMO UN FOTTUTISSIMO PROBLEMA");
		}
		IJ.log("zeroRight= " + zeroRight);
		IJ.log("pre1Right= " + pre1Right);
		IJ.log("pre2Right= " + pre2Right);
		IJ.log("zeroLeft= " + zeroLeft);
		IJ.log("pre1Left= " + pre1Left);
		IJ.log("pre2Left= " + pre2Left);

		double[][] zeri = new double[6][2];
		// wueste sono le X
		zeri[0][0] = zeroRight;
		zeri[1][0] = pre1Right;
		zeri[2][0] = pre2Right;
		zeri[3][0] = zeroLeft;
		zeri[4][0] = pre1Left;
		zeri[5][0] = pre2Left;
		// queste sono le Y
		zeri[0][1] = 0;
		zeri[1][1] = pip1;
		zeri[2][1] = pip2;
		zeri[3][1] = 0;
		zeri[4][1] = -pip1;
		zeri[5][1] = -pip2;

		return zeri;
	}

	public double[] determinePoints(double profile1[]) {

		double max = Double.MIN_VALUE;
		int maxpos = 0;
		double min = Double.MAX_VALUE;
		int minpos = 0;
		int left1 = 0;
		int right1 = 0;
		int left2 = 0;
		int right2 = 0;
		int left3 = 0;
		int right3 = 0;
		double[] out1 = new double[14];

		// il primo punto che determino e' il massimo, oltre al valore mi interessa la
		// posizione

		for (int i1 = 1; i1 < profile1.length - 1; i1++) {
			if (profile1[i1] > max) {
				max = profile1[i1];
				maxpos = i1;
			}
			if (profile1[i1] < min) {
				min = profile1[i1];
				minpos = i1;
			}
		}
		// determinato massimo e minimo calcolo il range
		double range = max - min;
		double value1 = min + range * 1 / 8;
		double value2 = min + range * 1 / 4;
		double value3 = min + range * 1 / 2;
//		IJ.log("STICAZZI max= " + max + " range= " + range + " maxpos= " + maxpos + "minpos= " + minpos);

		// una coppia di punti la metto a max-1/8 range
		// ora parto dal max
		for (int i1 = minpos; i1 > 0; i1--) {
			IJ.log("ricerca value1 i1= " + i1);
			if (profile1[i1] >= value1) {
				left1 = i1;
//				MyLog.waitHere("profile1[i1]= " + profile1[i1] + " value1" + value1 + " left1= " + left1);
				break;
			}
		}

		for (int i1 = minpos; i1 < profile1.length; i1++) {
			if (profile1[i1] >= value1) {
				right1 = i1;
//				MyLog.waitHere("profile1[i1]= " + profile1[i1] + " value1" + value1 + " right1= " + right1);
				break;
			}
		}

		// una coppia di punti la metto a max-1/4 range
		// ora parto dal max
		for (int i1 = minpos; i1 > 0; i1--) {
			if (profile1[i1] >= value2) {
				left2 = i1;
				break;
			}
		}
		for (int i1 = minpos; i1 < profile1.length; i1++) {
			if (profile1[i1] >= value2) {
				right2 = i1;
				break;
			}
		}

		// una coppia di punti la metto a max-1/4 range
		// ora parto dal max
		for (int i1 = minpos; i1 > 0; i1--) {
			if (profile1[i1] >= value3) {
				left3 = i1;
				break;
			}
		}
		for (int i1 = minpos; i1 < profile1.length; i1++) {
			if (profile1[i1] >= value3) {
				right3 = i1;
				break;
			}
		}
		// e ora come cazzabibbola li restituisco, visto che faceva comodo dare anche i
		// valori double?
		// IDEONA da volgare elettricista: tutto double, ole' e, come ho visto fare in
		// spline, faccio un unico array con i valori messi come lo dico io e basta !

		out1[0] = (double) left3;
		out1[1] = value3;
		out1[2] = (double) left2;
		out1[3] = value2;
		out1[4] = (double) left1;
		out1[5] = value1;
		out1[6] = (double) minpos;
		out1[7] = min;
		out1[8] = (double) right1;
		out1[9] = value1;
		out1[10] = (double) right2;
		out1[11] = value2;
		out1[12] = (double) right3;
		out1[13] = value3;
		MyLog.logVector(out1, "out1");

		return out1;
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
	public double[] spessStratoOLD(double R1, double R2, double sTeor, double dimPix2) {

		double spessArray[]; // qui verranno messi i risultati
		spessArray = new double[4];
		double S1 = R1 * Math.tan(Math.toRadians(11));
		double S2 = R2 * Math.tan(Math.toRadians(11));
		double Sen22 = Math.sin(Math.toRadians(22));
		double aux1 = -(S1 - S2) / (S1 + S2);
		double aux4 = Math.asin(Sen22 * aux1);
		double tilt1Ramp = Math.toDegrees(0.5 * aux4);
		double aux2 = Math.tan(Math.toRadians(11.0 - tilt1Ramp));
		double aux3 = Math.tan(Math.toRadians(11.0 + tilt1Ramp));
		double S1Cor = aux3 * R1;
		double S2Cor = aux2 * R2;
		double accurSpess = 100.0 * (S1Cor - sTeor) / sTeor;
		double erroreR1 = dimPix2 * aux3;
		double erroreR2 = dimPix2 * aux2;
		double erroreTot = Math.sqrt(erroreR1 * erroreR1 + erroreR2 * erroreR2);
		double erroreSper = 100.0 * erroreTot / sTeor;
		spessArray[0] = S1Cor;
		spessArray[1] = S2Cor;
		spessArray[2] = erroreSper;
		spessArray[3] = accurSpess;
		return spessArray;
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

	public double spessSimple(double fwhm, double sTeor, double dimPix2) {

//		double S1 = fwhm * Math.sin(Math.toRadians(11.3));
//		double S2 = fwhm * Math.cos(Math.toRadians(11.3));
//		double S3 = fwhm * Math.sin(Math.toRadians(22.6));
//		double S4 = fwhm * Math.cos(Math.toRadians(22.6));
//
//		double Sen22 = Math.sin(Math.toRadians(22.6));
//		double aux1 = Sen22 * S1;
//		aux1 = 0.5;
//
//		double aux4 = Math.asin(aux1);
//		double tilt1Ramp = Math.toDegrees(0.5 * aux4);
//		double aux3 = Math.tan(Math.toRadians(11.3 + tilt1Ramp));
		double aux3 = Math.tan(Math.toRadians(11.3));
		double spessore = aux3 * fwhm;

//		IJ.log("fwhm= " + fwhm);
//		IJ.log("S1= " + S1);
//		IJ.log("S2= " + S2);
//		IJ.log("S3= " + S3);
//		IJ.log("S4= " + S4);
//		IJ.log("Sen22= " + Sen22);
//		IJ.log("aux1= " + aux1);
//		IJ.log("aux4= " + aux4);
//		IJ.log("tilt1Ramp= " + tilt1Ramp);
//		IJ.log("aux3= " + aux3);
//		IJ.log("spessore= " + spessore);
//		MyLog.waitHere();

		return spessore;
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

}
