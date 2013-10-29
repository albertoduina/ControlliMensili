package contMensili;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.awt.Rectangle;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.StringTokenizer;

import utils.AboutBox;
import utils.MyConst;
import utils.MyLog;
import utils.TableCode;
import utils.TableSequence;
import utils.UtilAyv;
import utils.ButtonMessages;
import utils.InputOutput;
import utils.ReadDicom;
import utils.ReportStandardInfo;

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
 * Calcolo Contrast to Noise Ratio
 * 
 * Per salvare i dati in formato xls necessita di Excel_Writer.jar nella
 * directory plugins
 * 
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 * 
 * 
 */
public class p9rmn_ implements PlugIn, Measurements {

	static final int ABORT = 1;

	public static final String VERSION = "CNR";

	private static String TYPE = " >>  CONTROLLO CNR____________________";

	// ---------------------------"01234567890123456789012345678901234567890"

	/**
	 * directory dati, dove vengono memorizzati ayv.txt e Results1.xls
	 */
	private static String fileDir = "";

	/**
	 * tabella coi dati di ayv.txt (generati da Sequenze)
	 */
	String[][] strRiga3;

	/**
	 * tabella coi dati di codici.txt
	 */
	String[][] tabl;

	private final String TEST_DIRECTORY = "/test2/";

	private final String TEST_FILE = "test2.jar";

	// vettori con le coordinate centro predeterminate in pratica sulle
	// immagini
	//
	private final String X_CENTER_ROI_TESTSIEMENS = "97;148;42;94;144;196;39;91;142;193;88;139";

	private final String Y_CENTER_ROI_TESTSIEMENS = "39;41;87;91;92;96;139;143;145;147;194;197";

	private final String X_CENTER_ROI_TESTGE = "93;143;39;90;141;193;38;88;141;191;88;139";

	private final String Y_CENTER_ROI_TESTGE = "24;25;73;74;76;77;124;125;127;128;176;178";

	private final int N_GELS = 12;

	private final String SEQUENZE_FILE = "iw2ayv.txt";

	private final String XLS_FILE = "Result1.xls";

	private final String CODE_FILE = "/codici.txt";

	// VET_NUMERI_GEL contiene il numero identificativo della targhetta sui
	// GEL
	//
	private final int[] VET_NUMERI_GEL = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
			14 };

	// attenzione i seguenti POINTER_GELx_Tx sono dei puntatori ai dati
	// nei vettori, NON contengono il numero del gel
	//
	private final int[] POINTER_GEL1_T1 = { 3, 1, 10, 9, 11 };

	private final int[] POINTER_GEL2_T1 = { 4, 0, 6, 1, 0 };

	private final int[] POINTER_GEL1_T2 = { 2, 7, 1, 4, 11 };

	private final int[] POINTER_GEL2_T2 = { 8, 2, 0, 3, 0 };

	private final int TOKENS4 = 4;

	private final int MEAN = 2;

	private final int STD_DEV = 4;

	private final int MAX_NUMBER_OF_RESULTS = 5;

	private final int ROI_DIAM = 20;

	private final String DICOM_ECHO_TIME = "0018,0081";

	private final String DICOM_INVERSION_TIME = "0018,0082";

	private final String DICOM_PIXEL_SPACING = "0028,0030";

	private final String DICOM_ROWS = "0028,0010";

	private final String DICOM_COLUMNS = "0028,0011";

	private final double DIM_PIXEL_FOV_220 = 0.859375;

	public void run(String args) {
		
		UtilAyv.setMyPrecision();

		MyLog.appendLog(fileDir + "MyLog.txt", "p9 riceve "+args);

		//
		// CNR CON T1
		// GEL 4,5 deltaT1 10 ms bassissimo contrasto
		// GEL 2,1 deltaT1 110 ms medio contrasto
		// GEL 11,7 deltaT1 328 ms medio contrasto
		// GEL 10,2 deltaT1 491 ms bassissimo contrasto
		// GEL 14,1 deltaT1 1000 ms alto contrasto
		//
		// CNR CON T2
		//
		// GEL 3,9 deltaT2 3 ms bassissimo contrasto
		// GEL 8,3 deltaT2 18 ms medio contrasto
		// GEL 2,1 deltaT2 20 ms medio contrasto
		// GEL 5,4 deltaT2 50 ms alto contrasto
		// GEL 14,1 deltaT2 106 ms altissimo contrasto
		//
		// il CNR per immagini pesate T1 è valutato su una IR con TR=4000 ms,
		// TI=800 ms
		// il CNR per immagini pesate T2 è valutato su 3 immagini SE TR=2000 ms,
		// TE=45,90,180 ms
		//

		int userSelection1 = 0;
		int userSelection2 = 0;
		int userSelection3 = 0;

		boolean accetta = false;
		int riga1 = 0;
		boolean bstep = false;

		double[] medGels = null;
		double[] devGels = null;
		double[] vetCNR = null;
		double dimPixel = 0;

		boolean typeT2 = false;
		boolean testSiemens = false;
		boolean testGe = false;
		int[] vetXUpperLeftCornerRoiGels = new int[N_GELS];
		int[] vetYUpperLeftCornerRoiGels = new int[N_GELS];
		int[] vetRx = new int[N_GELS];
		int[] vetRy = new int[N_GELS];

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

		fileDir = Prefs.get("prefer.string1", "none");

		//
		// questo plugin è organizzato per ricevere solo i numeri di riga,
		// che fanno riferimento alla lista immagini COMMAND_FILE generata da
		// Sequenze, i codici della misura vengono letti dall'header
		// dell'immagine
		//
		InputOutput io = new InputOutput();

		// tabl = io.readFile1(CODE_FILE, TOKENS4);

		tabl = TableCode.loadTable(MyConst.CODE_FILE);

		StringTokenizer strTok = new StringTokenizer(args, "#");
		int nTokens = strTok.countTokens();
		boolean autoCalled = false;
		boolean manualCalled = false;
		if (nTokens > 0) {
			autoCalled = true;
			manualCalled = false;
		} else {
			autoCalled = false;
			manualCalled = true;
		}
		String path1 = "";
		String codice = "";

		boolean selftest = false;
		bstep = false;

		AboutBox ab = new AboutBox();
		boolean retry = false;
		if (manualCalled) {
			retry = false;
			do {
				userSelection1 = UtilAyv.userSelectionManual(VERSION, TYPE);
				switch (userSelection1) {
				case ABORT:
					ab.close();
					retry = false;
					return;
				case 2:
//					ab.about("Controllo CNR", this.getClass());
					ab.about("Controllo CNR", MyVersion.CURRENT_VERSION);
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
				if (io.checkJar(TEST_FILE)) {
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
				} else {
					UtilAyv.noTest2();
					testSiemens = false;
					testGe = false;
					return;
				}
			}

			if (!selftest) {
				//
				// poichè si tratta dell'utilizzo manuale del programma chiedo
				// quale immagine analizzare, in seguito chiedo se analizziamo
				// un T1 o un T2 (in questo modo mi svincolo dai dati
				// dell'immagine)
				//
				path1 = UtilAyv.imageSelection("SELEZIONARE IMMAGINE...");
				if (path1 == null)
					return;

				userSelection1 = ButtonMessages.ModalMsg(
						"Cosa vuoi elaborare?   <03>", "T1", "T2");
				switch (userSelection1) {
				case 1:
					typeT2 = true;
					break;
				case 2:
					typeT2 = false;
					break;
				}
			}
		}
		String home1 = "";
		String defaultVetGx = null;
		String defaultVetGy = null;

		if (testSiemens) {
			home1 = findTestImages();
			path1 = home1 + "/B080_testP2";
			nTokens = 1;
			defaultVetGx = X_CENTER_ROI_TESTSIEMENS;
			defaultVetGy = Y_CENTER_ROI_TESTSIEMENS;
		}
		if (testGe) {
			home1 = findTestImages();
			path1 = home1 + "/HT1A2_01testP2";
			nTokens = 1;
			defaultVetGx = X_CENTER_ROI_TESTGE;
			defaultVetGy = Y_CENTER_ROI_TESTGE;
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
//					ab.about("Contollo CNR", this.getClass());
					ab.about("Controllo CNR", MyVersion.CURRENT_VERSION);
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
		if (autoCalled) {
			if (bstep)
				ButtonMessages.ModelessMsg("Ricevuto=" + args, "CONTINUA");

			if (nTokens != 1) {
				ButtonMessages.ModelessMsg(VERSION
						+ " >> p9rmn ERRORE PARAMETRI CHIAMATA", "CHIUDI");
				IJ.log("p9rmn ERRORE PARAMETRI CHIAMATA =" + args);
				return;
			}
			riga1 = Integer.parseInt(strTok.nextToken());
			//
			// Carico la tabella SEQUENZE_FILE in memoria
			//
			strRiga3 = new TableSequence().loadTable(fileDir + SEQUENZE_FILE);

			path1 = TableSequence.getPath(strRiga3, riga1);
			codice = TableSequence.getCode(strRiga3, riga1);
			String cod = codice.substring(0, 2).trim();

			if (cod.equals("T2"))
				typeT2 = true;
			else
				typeT2 = false;

		}
		int misure1 = UtilAyv.setMeasure(MEAN + STD_DEV);
		String[][] info1 = ReportStandardInfo.getStandardInfo(strRiga3, riga1,
				tabl, VERSION, autoCalled);

		//
		// Qui si torna se la misura è da rifare
		//
		do {
			UtilAyv.closeResultsWindow();
			ImagePlus imp1 = UtilAyv.openImageMaximized(path1);

			int Rows = ReadDicom.readInt(ReadDicom.readDicomParameter(imp1,
					DICOM_ROWS));
			int Columns = ReadDicom.readInt(ReadDicom.readDicomParameter(imp1,
					DICOM_COLUMNS));

			dimPixel = ReadDicom
					.readDouble(ReadDicom.readSubstring(ReadDicom
							.readDicomParameter(imp1, DICOM_PIXEL_SPACING), 2));

			int roi_diam = (int) ((double) ROI_DIAM * DIM_PIXEL_FOV_220 / dimPixel);

			Roi[] vetRoi = new Roi[N_GELS];
			//
			// utilizziamo le preferenze di p2rmn, questo ci permette di
			// sfruttare il posizionamento già effettuato per p2, a patto che in
			// codici.txt venga messo p9rmn dopo p2rmn (le immagini in ayv.txt
			// sono ordinate da sequenze utilizzando codici.txt)
			//
			String saveVetXUpperLeftCornerRoiGels = Prefs.get("prefer.p2rmnGx",
					defaultVetGx);
			String saveVetYUpperLeftCornerRoiGels = Prefs.get("prefer.p2rmnGy",
					defaultVetGy);
			if (selftest) {
				saveVetXUpperLeftCornerRoiGels = defaultVetGx;
				saveVetYUpperLeftCornerRoiGels = defaultVetGy;
			}

			vetXUpperLeftCornerRoiGels = UtilAyv.getPos2(
					saveVetXUpperLeftCornerRoiGels, Columns);

			vetYUpperLeftCornerRoiGels = UtilAyv.getPos2(
					saveVetYUpperLeftCornerRoiGels, Rows);

			// vetXUpperLeftCornerRoiGels = UtilAyv
			// .getPos(saveVetXUpperLeftCornerRoiGels);
			// vetYUpperLeftCornerRoiGels = UtilAyv
			// .getPos(saveVetYUpperLeftCornerRoiGels);

			medGels = new double[vetXUpperLeftCornerRoiGels.length];
			devGels = new double[vetXUpperLeftCornerRoiGels.length];
			if (selftest) {
				// userSelection1 = utils.ModelessMsg("SELFTEST    <40>",
				// "CONTINUA");
			}

			for (int i1 = 0; i1 < vetRoi.length; i1++) {

				if (selftest) {
					imp1.setRoi(new OvalRoi(vetXUpperLeftCornerRoiGels[i1],
							vetYUpperLeftCornerRoiGels[i1], roi_diam, roi_diam,
							imp1));

					imp1.updateAndDraw();

				} else {

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

						imp1.setRoi(new OvalRoi(xRoi, yRoi, roi_diam, roi_diam,
								imp1));

						imp1.updateAndDraw();
						if (!selftest)
							userSelection1 = ButtonMessages.ModelessMsg(
									"Posizionare ROI su GEL" + (i1 + 1)
											+ "  e premere Accetta      <08>",
									"ACCETTA", "RIDISEGNA");
					} while (userSelection1 == 1);
				}

				ImageStatistics stat1 = imp1.getStatistics();
				ImageProcessor ip8 = imp1.getProcessor();
				Rectangle r8 = ip8.getRoi();
				vetXUpperLeftCornerRoiGels[i1] = r8.x;
				vetYUpperLeftCornerRoiGels[i1] = r8.y;
				vetRx[i1] = r8.width;
				vetRy[i1] = r8.height;
				vetRoi[i1] = imp1.getRoi();

				medGels[i1] = stat1.mean;
				devGels[i1] = stat1.stdDev;
			}

			// saveVetXUpperLeftCornerRoiGels = "";
			// saveVetYUpperLeftCornerRoiGels = "";
			// for (int i1 = 0; i1 < vetXUpperLeftCornerRoiGels.length; i1++) {
			// saveVetXUpperLeftCornerRoiGels = saveVetXUpperLeftCornerRoiGels
			// + vetXUpperLeftCornerRoiGels[i1] + ";";
			// saveVetYUpperLeftCornerRoiGels = saveVetYUpperLeftCornerRoiGels
			// + vetYUpperLeftCornerRoiGels[i1] + ";";
			// }

			saveVetXUpperLeftCornerRoiGels = UtilAyv.putPos2(
					vetXUpperLeftCornerRoiGels, Columns);
			saveVetYUpperLeftCornerRoiGels = UtilAyv.putPos2(
					vetYUpperLeftCornerRoiGels, Rows);

			Prefs.set("prefer.p2rmnGx", saveVetXUpperLeftCornerRoiGels);
			Prefs.set("prefer.p2rmnGy", saveVetYUpperLeftCornerRoiGels);

			vetCNR = new double[MAX_NUMBER_OF_RESULTS];

			int[] pointerGel1 = null;
			int[] pointerGel2 = null;

			if (typeT2) {
				pointerGel1 = POINTER_GEL1_T2;
				pointerGel2 = POINTER_GEL2_T2;
			} else {
				pointerGel1 = POINTER_GEL1_T1;
				pointerGel2 = POINTER_GEL2_T1;
			}
			for (int i1 = 0; i1 < pointerGel1.length; i1++) {
				// /////////////
				// MAIN_FORMULA
				// /////////////
				// CNR= ( medGel1 - medGel2 ) / sqrt( devGel1 ^2 + devGel2 ^2 )
				// ////////////
				vetCNR[i1] = (medGels[pointerGel1[i1]] - medGels[pointerGel2[i1]])
						/ (Math.sqrt(Math.pow(devGels[pointerGel1[i1]], 2)
								+ Math.pow(devGels[pointerGel2[i1]], 2)));
			}

			String t1 = "TESTO          ";
			ResultsTable rt = ReportStandardInfo.putStandardInfoRT(info1);
			int col = 0;
			rt.setHeading(++col, t1);
			rt.setHeading(++col, "VALORE      ");
			rt.setHeading(++col, "roi_x1");
			rt.setHeading(++col, "roi_y1");
			rt.setHeading(++col, "roi_r1");
			rt.setHeading(++col, "roi_x2");
			rt.setHeading(++col, "roi_y2");
			rt.setHeading(++col, "roi_r2");

			String label;
			double echo = ReadDicom.readDouble(ReadDicom.readDicomParameter(
					imp1, DICOM_ECHO_TIME));
			rt.addLabel(t1, "TE");
			rt.addValue(2, echo);
			rt.incrementCounter();

			double inversion = ReadDicom.readDouble(ReadDicom
					.readDicomParameter(imp1, DICOM_INVERSION_TIME));
			rt.addLabel(t1, "TI");
			rt.addValue(2, inversion);
			if (typeT2 && (inversion > 0))
				IJ.showMessage("la sequenza di "
						+ path1
						+ " dovrebbe essere una T2 ma ha un Inversion Time > 0!");
			for (int i1 = 0; i1 < pointerGel1.length; i1++) {
				rt.incrementCounter();
				label = "gels_" + VET_NUMERI_GEL[pointerGel1[i1]] + "-"
						+ VET_NUMERI_GEL[pointerGel2[i1]];
				rt.addLabel(t1, label);
				rt.addValue(2, vetCNR[i1]);
				rt.addValue(3, vetXUpperLeftCornerRoiGels[pointerGel1[i1]]);
				rt.addValue(4, vetYUpperLeftCornerRoiGels[pointerGel1[i1]]);
				rt.addValue(5, vetRx[pointerGel1[i1]]);
				rt.addValue(6, vetXUpperLeftCornerRoiGels[pointerGel2[i1]]);
				rt.addValue(7, vetYUpperLeftCornerRoiGels[pointerGel2[i1]]);
				rt.addValue(8, vetRx[pointerGel2[i1]]);
			}

			rt.show("Results");

			if (autoCalled) {
				userSelection3 = ButtonMessages.ModelessMsg(
						"Accettare il risultato delle misure?     <11>",
						"ACCETTA", "RIFAI");
				switch (userSelection3) {
				case 1:
					UtilAyv.cleanUp();
					accetta = false;
					break;
				case 2:
					accetta = true;
					break;
				}

			} else {
				if (selftest) {
					if (testSiemens)
						testSymphony(medGels[1], devGels[1], medGels[0],
								devGels[0], vetCNR[1]);

					if (testGe)
						testGe(medGels[1], devGels[1], medGels[0], devGels[0],
								vetCNR[2]);
				} else {
					userSelection3 = ButtonMessages
							.ModelessMsg(
									"Fine programma, in modo STAND-ALONE salvare A MANO la finestra Risultati    <12>",
									"CONTINUA");
					System.gc();
				}
				accetta = true;
			}
		} while (!accetta); // do

		//
		// Salvataggio dei risultati
		//

		if (autoCalled) {

			try {
				mySaveAs(fileDir + MyConst.TXT_FILE);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// IJ.run("Excel...", "select...=[" + fileDir + XLS_FILE + "]");
			TableSequence lr = new TableSequence();
			lr.putDone(strRiga3, riga1);
			lr.writeTable(fileDir + SEQUENZE_FILE, strRiga3);
		}
		UtilAyv.resetResultsTable();
		UtilAyv.resetMeasure(misure1);
		InputOutput.deleteDir(new File(TEST_DIRECTORY));
		if (autoCalled)
			UtilAyv.cleanUp();
	} // close run

	/**
	 * Saves this ResultsTable as a tab or comma delimited text file. The table
	 * is saved as a CSV (comma-separated values) file if 'path' ends with
	 * ".csv". Displays a file save dialog if 'path' is empty or null. Does
	 * nothing if the table is empty.
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
	 * built-in test per la immagine Symphony in modo PROVA
	 * 
	 * @param medGel_a
	 *            media gel2
	 * @param devGel_a
	 *            devStan gel2
	 * @param medGel_b
	 *            media gel1
	 * @param devGel_b
	 *            devstan gel1
	 * @param cnrGel_ab
	 *            cnrGel 1-2
	 */
	private void testSymphony(double medGel_a, double devGel_a,
			double medGel_b, double devGel_b, double cnrGel_ab) {
		boolean testok = true;

		double rightValue = 1647.762658227848;
		if (medGel_a != rightValue) {
			IJ.log("medGel2 ERRATA " + medGel_a + " anzichè " + rightValue);
			testok = false;
		}
		rightValue = 59.41805613461411;
		if (devGel_a != rightValue) {
			IJ.log("devGel2 ERRATA > " + devGel_a + " anzichè " + rightValue);
			testok = false;

		}
		rightValue = 1603.3101265822784;
		if (medGel_b != rightValue) {
			IJ.log("medGel1 ERRATA  > " + medGel_b + " anzichè " + rightValue);
			testok = false;
		}
		rightValue = 55.54885187603639;
		if (devGel_b != rightValue) {
			IJ.log("devGel1 ERRATA > " + devGel_b + " anzichè " + rightValue);
			testok = false;
		}
		rightValue = 0.5465033551900027;
		if (cnrGel_ab != rightValue) {
			IJ.log("cnrGel_2_1 ERRATA  > " + cnrGel_ab + " anzichè "
					+ rightValue);
			testok = false;
		}
		if (testok == true)
			ButtonMessages.ModelessMsg("Fine SelfTest TUTTO OK  <42>",
					"CONTINUA");
		else
			ButtonMessages.ModelessMsg(
					"Fine SelfTest CON ERRORI, vedi LOG  <43>", "CONTINUA");
		UtilAyv.cleanUp();

	} // testSymphony

	/**
	 * built-in test per la immagine GE in modo PROVA
	 * 
	 * @param medGel_a
	 *            media gel2
	 * @param devGel_a
	 *            devStan gel2
	 * @param medGel_b
	 *            media gel1
	 * @param devGel_b
	 *            devstan gel1
	 * @param cnrGel_ab
	 *            cnrGel 1-2
	 */
	private void testGe(double medGel_a, double devGel_a, double medGel_b,
			double devGel_b, double cnrGel_ab) {
		boolean testok = true;

		double rightValue = 733.5474683544304;
		if (medGel_a != rightValue) {
			IJ.log("medGel2 ERRATA " + medGel_a + " anzichè " + rightValue);
			testok = false;
		}
		rightValue = 24.3458081509488;
		if (devGel_a != rightValue) {
			IJ.log("devGel2 ERRATA > " + devGel_a + " anzichè " + rightValue);
			testok = false;

		}
		rightValue = 590.4746835443038;
		if (medGel_b != rightValue) {
			IJ.log("medGel1 ERRATA  > " + medGel_b + " anzichè " + rightValue);
			testok = false;
		}
		rightValue = 19.717432257273583;
		if (devGel_b != rightValue) {
			IJ.log("devGel1 ERRATA > " + devGel_b + " anzichè " + rightValue);
			testok = false;
		}
		rightValue = 2.150170864842611;
		if (cnrGel_ab != rightValue) {
			IJ.log("cnrGel_2_1 ERRATA  > " + cnrGel_ab + " anzichè "
					+ rightValue);
			testok = false;
		}
		if (testok == true)
			ButtonMessages.ModelessMsg("Fine SelfTest TUTTO OK  <42>",
					"CONTINUA");
		else
			ButtonMessages.ModelessMsg(
					"Fine SelfTest CON ERRORI, vedi LOG  <43>", "CONTINUA");
		UtilAyv.cleanUp();

	} // testGe

	/**
	 * genera una directory temporanea e vi estrae le immagini di test da
	 * test2.jar
	 * 
	 * @return home1 path della directory temporanea con le immagini di test
	 */
	private String findTestImages() {
		InputOutput io = new InputOutput();

		io.extractFromJAR(TEST_FILE, "B080_testP2", "./Test2/");
		io.extractFromJAR(TEST_FILE, "HT1A2_01testP2", "./Test2/");
		String home1 = this.getClass().getResource(TEST_DIRECTORY).getPath();
		return (home1);
	} // findTestImages

} // p9rmn
