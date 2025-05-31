package contMensili;

import java.awt.Frame;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.io.DirectoryChooser;
import ij.io.Opener;
import ij.plugin.PlugIn;
import ij.text.TextWindow;
import utils.AboutBox;
import utils.ArrayUtils;
import utils.ButtonMessages;
import utils.InputOutput;
import utils.MyConst;
import utils.MyLog;
import utils.ReadDicom;
import utils.TableCode;
import utils.TableExpand;
import utils.TableSequence;
import utils.TableSorter;
import utils.TableUtils;
import utils.TableVerify;
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
 *
 *
 * Main plugin for the Quality Controls of MRI images.
 *
 * The program, for a new search, make a recursive search in the selected
 * directory and sub-directories, write the list of processing files in ayv.txt.
 * After that it uses the list in ayv.txt to call the right plugin for
 * processing the different types of image.
 *
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 *
 */
public class Sequenze_OLD implements PlugIn {
	private final static int ABORT = 1;

	public static String VERSION = "Programma gestione automatica";

	public int location;
	//
	// ----------------------------------------------------------
	// ABILITA E DISABILITA LE STAMPE DI DEBUG
	// METTERE debugTables A FALSE PER NON AVERE LE STAMPE
	// ----------------------------------------------------------
	//
	public boolean debugTables = false;
	public static boolean forcesilent = false;

	public static boolean blackbox = false;
	public static String blackpath = "";
	public static String blackname = "";
	public static String blacklog = "";
	// public static String testP6_1 = "contMensili.p6rmn_ORIGINAL";
	// public static String testP6_2 = "contMensili.p6rmn_IMPROVED";
	// public static String testP6_3 = "contMensili.p6rmn_FITTER";
	public static int testP6 = 2; /// <<< SELEZIONA QUI
	public static String choice = "";

	@Override
	public void run(String arg) {
		// ============================================================================================
		// nota bene: le seguenti istruzioni devono ASSOLUTAMENTE essere all'inizio, in
		// questo modo il messaggio viene emesso, altrimenti si ha una eccezione
		// --------------------------------------------------------------------------------------------
		try {
			Class.forName("utils.IW2AYV");
		} catch (ClassNotFoundException e) {
			IJ.error("ATTENZIONE, manca il file iw2ayv_xxx.jar");
			return;
		}
		if (!UtilAyv.jarCount("iw2ayv_")) {
			return;
			// ============================================================================================
			// EQUIVALENZE DURANTE I TEST
		}

		// ==============================================================================================

		UtilAyv.setMyPrecision();
		// MyFileLogger.logger.info("<-- INIZIO Sequenze -->");
		TextWindow tw = new TextWindow("Sequenze", "<-- INIZIO Sequenze -->", 300, 200);
		Frame lw = WindowManager.getFrame("Sequenze");
		if (lw == null) {
			return;
		}
		lw.setLocation(10, 10);

		String startingDir = Prefs.get(MyConst.PREFERENCES_1, MyConst.DEFAULT_PATH);
		// IJ.log("Sequenze_>>> startingDir letta= " + startingDir);

		UtilAyv.logResizer();

		boolean startingDirExist = new File(startingDir).exists();

		// String[][] tableCode = TableCode.loadMultipleTable(MyConst.CODE_GROUP);

		/// --------------------------------------------------------------------------------------------
		/// ATTENZIONE L'ATTUALE VERSIONE DI SEQUENZE SUPPORTA UN UNICO FILE CON I
		/// CODICI, ESSO SI CHIAMA ATTUALMENTE CODICI090218.CSV E DELLA SUA MANUTENZIONE
		/// NON SI OCCUPA PIU'IL PROGRAMMATORE, MA GLI UTILIZZATORI, CHE SI OCCUPERANNO
		/// DEL SUO AGGIORNAMENTO E DELL'INTERSCAMBIO DELLA VERSIONE AGGIORNATA. QUESTO
		/// PERCHE'MI E'STATO CHIESTO DI RENDERE IL FILE CON I CODICI LIBERAMENTE
		/// ACCESSIBILE, QUINDI DA GENNAIO 2018 NON E' PIU' NELLE MIE DISPONIBILITA'
		/// (QUINDI LO POSSONO MODIFICARE TUTTI, INCLUSI D&P (DOGS & PIGS))
		/// --------------------------------------------------------------------------------------------

		TableCode tc1 = new TableCode();
		String[][] tableCode = tc1.loadMultipleTable("codici", ".csv");

		TableCode tc2 = new TableCode();
		boolean flag = tc2.ricercaDoppioni(tableCode);
		if (flag) {
			MyLog.waitHere("E VUALA': doppione rilevato in codici.csv");
		}

		if (debugTables) {
			IJ.log("\\Clear");
			MyLog.logMatrix(tableCode, "tableCode");
			MyLog.waitHere("salvare il log come tableCodeLoaded");
		}

//		String[][] tableExpand = TableExpand.loadTable(MyConst.EXPAND_FILE);
		TableExpand te1 = new TableExpand();
		String[][] tableExpand = te1.loadTableNew("expand", ".csv");

		new AboutBox().about("Scansione automatica cartelle", MyVersion.CURRENT_VERSION);
		IJ.wait(2000);
		new AboutBox().close();

		boolean nuovo1 = false;
		boolean nuovo2 = false;
		boolean self1 = false;
		boolean p10p11p12 = false;
		boolean fast = false;
		// boolean batch = false;
		boolean superficiali = false;
		boolean aux2 = false;
		boolean aux3 = false;
		choice = Prefs.get("prefer.choice", "none");

		// String[] items = { "p6rmn_ORIGINAL", "p6rmn_IMPROVED", "p6rmn_FITTER" };

		List<String> arrayStartingDir = new ArrayList<String>();

		GenericDialog gd = new GenericDialog("", IJ.getInstance());
		gd.addCheckbox("Nuovo controllo", aux2);
		gd.addCheckbox("SelfTest", false);
		gd.addCheckbox("p10_ p11_ p12_ p16_ p17_ p19_", true);
		gd.addCheckbox("Fast", true);
		gd.addCheckbox("Superficiali", false);
		// gd.addChoice(" ", items, choice);
		gd.showDialog();
		if (gd.wasCanceled()) {
			return;
		}

		nuovo2 = gd.getNextBoolean();
		self1 = gd.getNextBoolean();
		p10p11p12 = gd.getNextBoolean();
		fast = gd.getNextBoolean();
		superficiali = gd.getNextBoolean();
		// choice = gd.getNextChoice();
		if (fast) {
			Prefs.set("prefer.fast", "true");
		} else {
			Prefs.set("prefer.fast", "false");
		}
		Prefs.set("prefer.choice", choice);

//		IJ.log("CHOICE= " + choice);

		if (self1) {
			if (!new InputOutput().checkJar(MyConst.TEST_FILE)) {
				UtilAyv.noTest2();
				return;
			}

			// --------------------------------------------------------------------------------------------
			IJ.runPlugIn("contMensili.p3rmn_", "-1");
			IJ.runPlugIn("contMensili.p4rmn_", "-1");
			IJ.runPlugIn("contMensili.p5rmn_", "-1");
//			IJ.runPlugIn("contMensili.p6rmn_ORIGINAL", "-1");
			IJ.runPlugIn("contMensili.p6rmn_ORIGINAL", "-1");
			IJ.runPlugIn("contMensili.p7rmn_", "-1");
			IJ.runPlugIn("contMensili.p8rmn_", "-1");
			IJ.runPlugIn("contMensili.p10rmn_", "-1");
			IJ.runPlugIn("contMensili.p11rmn_", "-1");
			IJ.runPlugIn("contMensili.p12rmn_", "-1");
			IJ.runPlugIn("contMensili.p16rmn_", "-1");
			IJ.runPlugIn("contMensili.p19rmn_", "-1");
			IJ.runPlugIn("contMensili.p20rmn_", "-1");
			IJ.runPlugIn("contMensili.p10rmn_OLD1", "-1");
			IJ.runPlugIn("contMensili.p11rmn_OLD1", "-1");
			IJ.runPlugIn("contMensili.p12rmn_OLD1", "-1");
			// --------------------------------------------------------------------------------------------

			ButtonMessages.ModelessMsg("Sequenze: fine selfTest, vedere Log per risultati", "CONTINUA");
			return;
		}

		if (nuovo2 || !startingDirExist) {
			nuovo1 = true;
			aux2 = true;
			aux3 = true;
			DirectoryChooser.setDefaultDirectory(startingDir);
			DirectoryChooser od1 = new DirectoryChooser("Selezionare la cartella: ");
			startingDir = od1.getDirectory();

			if (startingDir == null) {
				return;
			}
			Prefs.set("prefer.string1", startingDir);
			arrayStartingDir.add(startingDir);
		} else {
			arrayStartingDir.add(startingDir);
		}

		long startTime = System.currentTimeMillis();
		for (String element : arrayStartingDir) {
			startingDir = element;

			boolean fileExist = new File(startingDir + MyConst.SEQUENZE_FILE).exists();

			if ((nuovo1) && (fileExist)) {
				new ButtonMessages();
				int userSelection1 = ButtonMessages.ModelessMsg(
						"Attenzione, nella cartella selezionata i files iw2ayv.txt e Results1.xls" + "\n"
								+ "esistono gia'  premere SOVRASCRIVI per cancellarli, CONTINUA per utilizzarli, altrimenti CHIUDI",
						"SOVRASCRIVI", "CONTINUA", "CHIUDI");
				switch (userSelection1) {
				case ABORT:
					return;
				case 2:
					nuovo1 = false;
					break;
				case 3:
					nuovo1 = true;
					break;
				}
			}

			if (!fileExist) {
				nuovo1 = true;
			}

			if (nuovo1) {
				//
				// se e' stato selezionato un nuovo set di misure cancello sia
				// il file directory che il file excel dei risultati
				//
				File fx = new File(startingDir + MyConst.SEQUENZE_FILE);
				if (fx.exists()) {
					fx.delete();
				}
				File fy = new File(startingDir + MyConst.XLS_FILE);
				if (fy.exists()) {
					fy.delete();
				}
				File fz = new File(startingDir + MyConst.TXT_FILE);
				if (fz.exists()) {
					fz.delete();
				}
				MyLog.initLog(startingDir + "MyLog.txt");

				Prefs.set("prefer.p6rmnSTART", true);
				// MyLog.waitHere("metto valid a false");
				Prefs.set("prefer.p16rmn_valid", false);

				List<File> result = getFileListing(new File(startingDir));
				if (result == null) {
					MyLog.here("getFileListing.result==null");
				}
				String[] list = new String[result.size()];
				int j1 = 0;
				for (File file : result) {
					list[j1++] = file.getPath();
				}

				String[][] tableSequenceLoaded = generateSequenceTable(list, tableCode, tableExpand);
				if (debugTables) {
					IJ.log("\\Clear");
					MyLog.logMatrix(tableSequenceLoaded, "tableSequenceLoaded");
					MyLog.waitHere("salvare il log come TableSequenceLoaded");
				}

				if (tableSequenceLoaded == null) {
					MyLog.waitHere("non sono state trovate immagini da analizzare");
					return;
				}

				// =============================================================
				// NUOVA CAZZATA INEDITA
				// =============================================================

				treviglioDevelop(tableSequenceLoaded, tableCode);

				if (debugTables) {
					IJ.log("\\Clear");
					MyLog.logMatrix(tableSequenceLoaded, "tableSequenceTreviglio");
					MyLog.waitHere("salvare il log come TableSequenceTreviglio");
					IJ.log("\\Clear");
					MyLog.logMatrix(tableCode, "tableCodeTreviglio");
					MyLog.waitHere("salvare il log come TableCodeTreviglio");
				}

				// String[][] tableSequenceTreviglio =
				// treviglioSequenceTable(tableSequenceLoaded, tableExpand);
				// --------------------------------------------------------------------------------------------
				// eseguo un sort in base a POSIZ, TIME, poi riordino in base a tableCode infine
				// verifico che siano presenti i giusti numeri di immagini. Per ultimo
				// modifierSmart si occupa delle acquisizioni con più fette
				// --------------------------------------------------------------------------------------------

				String[][] tableSequenceSorted1 = TableSorter.minsortDouble(tableSequenceLoaded, TableSequence.POSIZ,"");
				if (debugTables) {
					IJ.log("\\Clear");
					MyLog.logMatrix(tableSequenceSorted1, "tableSequenceSorted1");
					MyLog.waitHere("salvare il log come TableSequenceSorted1");
				}

				String[][] tableSequenceSorted2 = TableSorter.minsortDouble(tableSequenceSorted1, TableSequence.TIME,"");
				if (debugTables) {
					IJ.log("\\Clear");
					MyLog.logMatrix(tableSequenceSorted2, "tableSequenceSorted2");
					MyLog.waitHere("salvare il log come TableSequenceSorted2");
				}

				String[][] tableSequenceReordered = reorderSequenceTable(tableSequenceSorted2, tableCode);
				if (debugTables) {
					IJ.log("\\Clear");
					MyLog.logMatrix(tableSequenceReordered, "tableSequenceReordered");
					MyLog.waitHere("salvare il log come TableSequenceReordered");
				}

				String[][] listProblems = verifySequenceTable(tableSequenceReordered, tableCode);
				if (debugTables) {
					IJ.log("\\Clear");
					MyLog.logMatrix(listProblems, "listProblems");
					MyLog.waitHere("salvare il log come ListProblems");
				}

				String[] myCode1 = { "BL2F_", "BL2S_", "BR2F_", "BR2S_", "YL2F_", "YL2S_", "YR2F_", "YR2S_", "JUS1A",
						"JUSAA", "KUS1A", "KUSAA", "PUSAA", "PUS1A" };

				String[][] tableSequenceModified1 = TableSorter.tableModifierSmart(tableSequenceReordered, myCode1);

				if (debugTables) {
					IJ.log("\\Clear");
					MyLog.logMatrix(tableSequenceModified1, "tableSequenceModified1");
					MyLog.waitHere("salvare il log come tableSequenceModified1");
				}

				// NOTA BENE: lasciare test a false, altrimenti non vengono piu'
				// stampati gli errori e si hanno problemi in elaborazione!!!
				boolean test = false;
				logVerifySequenceTable(listProblems, test);

				boolean success = new TableSequence().writeTable(startingDir + MyConst.SEQUENZE_FILE,
						tableSequenceModified1);
				if (!success) {
					IJ.log("Problemi creazione file iw2ayv.txt");
				}
			}

			String[][] tableSequenceReloaded = new TableSequence().loadTable(startingDir + MyConst.SEQUENZE_FILE);
			if (debugTables) {
				IJ.log("\\Clear");
				MyLog.logMatrix(tableSequenceReloaded, "tableSequenceReloaded");
				MyLog.waitHere("salvare il log come tableSequenceReloaded");
			}

			// ECCO COME RINCOGLIONIRSI E PERDERE UN ORA, IL PLUGIN VIENE LANCIATO IN
			// AUTOMATICO DA QUI, BISOGNA FARE QUI EVENTUALI MAGHEGGI SUI NOMI DURATE I TEST

			// --------------------------------------------------------------------------------------------
			// motore di chiamata dei plugins
			// --------------------------------------------------------------------------------------------
			callPluginsFromSequenceTable(tableSequenceReloaded, tableCode, false, superficiali, p10p11p12, tw);
			// --------------------------------------------------------------------------------------------

		}
		long endTime = System.currentTimeMillis();
		long total = (endTime - startTime) / 1000;
		long minuti = total / 60;
		long secondi = total - minuti * 60;

		IJ.beep();
		IJ.wait(100);
		IJ.beep();
		IJ.wait(100);
		IJ.beep();
		IJ.wait(100);
		IJ.beep();

		MyLog.waitHere("FINE LAVORO " + minuti + " minuti " + secondi + " secondi");
	}

	/**
	 * Legge ricorsivamente la directory e relative sottodirectory
	 *
	 * copied from www.javapractices.com (Alex Wong
	 *
	 * @param startingDir directory "radice"
	 * @return lista dei path dei file
	 */
	public List<File> getFileListing(File startingDir) {
		List<File> result = new ArrayList<File>();
		File[] filesAndDirs = startingDir.listFiles();
		if (filesAndDirs == null) {
			return null;
		}
		List<File> filesDirs = Arrays.asList(filesAndDirs);
		for (File file : filesDirs) {
			if (!file.isFile()) {
				// must be a directory
				// recursive call !!
				List<File> deeperList = getFileListing(file);
				result.addAll(deeperList);
			} else {
				result.add(file);
			}
		}
		return result;
	}

	/**
	 * Analizza tutti i file e, se sono compatibili con i codici di sequenza
	 * elencati in codiciNew.csv, compila i vari campi della tabella dei file da
	 * analizzare
	 *
	 * @param pathList     lista dei file presenti nelle directory e relative
	 *                     sottodirectory selezionate.
	 * @param tableCode2   contenuto di codici.txt
	 * @param tableExpand4 contenuto di expand.txt
	 * @return restituisce una TableSequence le cui colonne contengono: vetConta,
	 *         vetPath, vetCodice, vetCoil, vetImaDaPassare, vetimaGruppo,
	 */

	public String[][] generateSequenceTable(String[] pathList, String[][] tableCode2, String[][] tableExpand4) {

		List<String> vetConta = new ArrayList<String>();
		List<String> vetPath = new ArrayList<String>();
		List<String> vetCodice = new ArrayList<String>();
		List<String> vetCoil = new ArrayList<String>();
		List<String> vetImaDaPassare = new ArrayList<String>();
		List<String> vetImaOrder = new ArrayList<String>();
		List<String> vetImaIncrement = new ArrayList<String>();
		List<String> vetSpare_1 = new ArrayList<String>();
		List<String> vetSpare_2 = new ArrayList<String>();
		List<String> vetSpare_3 = new ArrayList<String>();
		List<String> vetSerie = new ArrayList<String>();
		List<String> vetAcq = new ArrayList<String>();
		List<String> vetIma = new ArrayList<String>();
		List<String> vetAcqTime = new ArrayList<String>();
		List<String> vetEchoTime = new ArrayList<String>();
		List<String> vetSlicePosition = new ArrayList<String>();
		List<String> vetDone = new ArrayList<String>();
		List<String> vetDirez = new ArrayList<String>();
		List<String> vetProfond = new ArrayList<String>();

		if (pathList == null) {
			IJ.log("loadList2.pathList = null");
			return null;
		}
		if (tableCode2 == null) {
			IJ.log("loadList2.tableCode2 = null");
			return null;
		}

		int count3 = 0;
		for (int i1 = 0; i1 < pathList.length; i1++) {

			String aux1 = "generateSequenceTable " + i1 + " / " + pathList.length;

			IJ.redirectErrorMessages();

			IJ.showStatus(aux1);

			// boolean questo = false;

			int type = (new Opener()).getFileType(pathList[i1]);

			if (type == Opener.DICOM) {

				ImagePlus imp1 = new Opener().openImage(pathList[i1]);

				// if (imp1.getTitle().equals(
				// "15_18130558_HE3,4vNE1,2vSP1,2_O12S_")) {
				// // MyLog.waitHere("15_18130558_HE3,4vNE1,2vSP1,2_O12S_");
				// questo = true;
				// }
				//
				// if (questo)
				// MyLog.waitHere("trovato");

				if ((imp1 == null) || !ReadDicom.hasHeader(imp1)) {
					// IJ.log("" + i1 + " file " + pathList[i1] + " not dicom");
					continue;
				}

				String path1 = pathList[i1];
				// MyLog.waitHere("path1= "+path1);

				String fileName = path1.substring(path1.lastIndexOf("\\") + 1, path1.length());
				// MyLog.waitHere("fileName= "+fileName);

				String codice = "-----";
				String subCodice = "-----";
				String blob1 = "DelRe";
				if (fileName.length() >= 5) {
					subCodice = fileName.substring(0, 5).trim();
				}
				// MyLog.waitHere("subCodice= "+subCodice);
				if (InputOutput.isCode(subCodice, tableCode2)) {
					codice = subCodice;
				} else {
					String seriesDescription = ReadDicom.readDicomParameter(imp1, MyConst.DICOM_SERIES_DESCRIPTION);
					if (seriesDescription.length() >= 5) {
						codice = seriesDescription.substring(0, 5).trim();
						// la seguente modifica permette di leggere le ultime 5 lettere dei codici che
						// iniziano con "DelRe", modifica per il CAE (11/08/2023)- non dovrebbe
						// intervenire in nessun altro caso
						if (codice.equalsIgnoreCase(blob1)) {
							int len1 = seriesDescription.length();
							codice = seriesDescription.substring(len1 - 5, len1);
						}
					}
				}

//				if (ReadDicom.isDicomEnhanced(imp1))
//					IJ.log("DICOM_ENHANCED");

				// String coil = ReadDicom.getFirstCoil(imp1);
				String coil = ReadDicom.getAllCoils(imp1);

				if (coil.equals("MISSING")) {
					coil = new UtilAyv().kludge(pathList[i1]);
				}

				// ###########################################################################
				// 16/02/2024 modifiche per la SOLA (romanesco)

				String firstLetterOfCoil = coil.substring(0, 1);

				// MyLog.waitHere("codice= "+codice);

				// ===============================================================================

				if ((codice.equalsIgnoreCase("BL2F_") && firstLetterOfCoil.equalsIgnoreCase("R"))) {
					// MyLog.waitHere("BL2F XXX");
					coil = "XXX";
				}
				if ((codice.equalsIgnoreCase("BR2F_") && firstLetterOfCoil.equalsIgnoreCase("L"))) {
					// MyLog.waitHere("BR2F XXX");
					coil = "XXX";
				}
				if ((codice.equalsIgnoreCase("BL2S_") && firstLetterOfCoil.equalsIgnoreCase("R"))) {
					// MyLog.waitHere("BL2F XXX");
					coil = "XXX";
				}
				if ((codice.equalsIgnoreCase("BR2S_") && firstLetterOfCoil.equalsIgnoreCase("L"))) {
					// MyLog.waitHere("BR2F XXX");
					coil = "XXX";
				}

				// --------------------------------------------------------------------------------------------
				// sostituisco il ; col simbolo +
				// --------------------------------------------------------------------------------------------

				coil = coil.replace("BAL;BAR;BCL;BCR", "BAL+BAR+BCL+BCR");
				coil = coil.replace("BL;BR", "BL+BR");
				coil = coil.replace("PL1;PR1", "PL1+PR1");
				coil = coil.replace("PL2;PR2", "PL2+PR2");
				coil = coil.replace("PL3;PR3", "PL3+PR3");
				coil = coil.replace("PL4;PR4", "PL4+PR4");

				// ###########################################################################

				// ===============================================================================

				String[] allCoils = ReadDicom.parseString(coil);

				String numSerie = ReadDicom.readDicomParameter(imp1, MyConst.DICOM_SERIES_NUMBER);
				String numAcq = ReadDicom.readDicomParameter(imp1, MyConst.DICOM_ACQUISITION_NUMBER);
				String numIma = ReadDicom.readDicomParameter(imp1, MyConst.DICOM_IMAGE_NUMBER);

				// String acqTime = deleteDot(ReadDicom.readDicomParameter(imp1,
				// MyConst.DICOM_ACQUISITION_TIME));

				String acqTime = deleteDot(readTime(imp1));

				String echoTime = ReadDicom.readDicomParameter(imp1, MyConst.DICOM_ECHO_TIME,
						MyConst.DICOM_EFFECTIVE_ECHO_TIME);
				if (echoTime.compareTo("") == 0) {
					echoTime = "0";
				}
				String slicePosition = ReadDicom.readDicomParameter(imp1, MyConst.DICOM_SLICE_LOCATION);

				String done = "0";
				boolean trovato = false;
				int tableRow = 0;
				// MyLog.logMatrix(tableCode2, "tableCode2");
				// MyLog.waitHere();

				for (int j1 = 0; j1 < tableCode2.length; j1++) {

					if (codice.equals(tableCode2[j1][TableCode.CODE])) {
						// IJ.log("tableCode2[j1][TableCode.COIL]= " +
						// tableCode2[j1][TableCode.COIL] + "___coil= "
						// + coil);

						if ((tableCode2[j1][TableCode.COIL].equals("x"))
								|| (tableCode2[j1][TableCode.COIL].equals("xxx"))
								|| (tableCode2[j1][TableCode.COIL].equals(coil))
								|| coilPresent(allCoils, tableCode2[j1][TableCode.COIL])) {

							//
							// E' QUI'LA FESTA ? cerrtoo
							//
							String myString = tableCode2[j1][TableCode.IMA_ORDER];
							String[] myNums = myString.split("x");

							boolean trovato1 = false;
							for (String myNum : myNums) {
								if (myNum.equals(numIma)) {
									trovato1 = true;
								}
							}

							if (myNums[0].equals("0") || trovato1) {

								tableRow = j1;
								trovato = true;

								break;
							}
						}
					}
				}
				if (trovato) {
					// if (questo)
					// MyLog.waitHere("leggo questo");
					// IJ.log(tableCode2[tableRow][0] + " " + coil + " "
					// + tableCode2[tableRow][4]);
					// count3++;
					// se il codice e' conosciuto aggiunge i dati
					vetConta.add("" + (count3));
					count3++;
					vetPath.add(path1);
					vetCodice.add(codice);
					// if (questo) MyLog.waitHere(""+count3+" coil= "+coil);
					vetCoil.add(coil);
					vetImaDaPassare.add(tableCode2[tableRow][TableCode.IMA_PASS]);
					vetImaOrder.add(tableCode2[tableRow][TableCode.IMA_ORDER]);
					vetImaIncrement.add(tableCode2[tableRow][TableCode.IMA_INCREMENT]);
					vetSpare_1.add(tableCode2[tableRow][TableCode.SPARE_1]);
					vetSpare_2.add(tableCode2[tableRow][TableCode.SPARE_2]);
					vetSpare_3.add(tableCode2[tableRow][TableCode.SPARE_3]);
					vetSerie.add(numSerie);
					vetAcq.add(numAcq);
					vetIma.add(numIma);
					vetAcqTime.add(acqTime);
					vetEchoTime.add(echoTime);
					vetSlicePosition.add(slicePosition);
					vetDirez.add(tableCode2[tableRow][TableCode.DIREZ]);
					vetProfond.add(tableCode2[tableRow][TableCode.PROFOND]);
					vetDone.add(done);
					String[][] espansione;
					// vedo se occorre espandere
					espansione = expandCode(codice, echoTime, tableExpand4);

					// List<String> vetOldCode = new ArrayList<String>();
					// List<String> vetEcho = new ArrayList<String>();
					// List<String> vetNewCode = new ArrayList<String>();
					// List<String> vetImaPass = new ArrayList<String>();

					if (espansione != null) {
						for (String[] element : espansione) {
							// count3++;
							vetConta.add("" + (count3));
							count3++;
							vetPath.add(path1);
//							IJ.log("espando con " + espansione[i2][2] + " " + espansione[i2][3]);
							vetCodice.add(element[2]);
							vetCoil.add(coil);
							vetImaDaPassare.add(element[3]);
							vetImaOrder.add(tableCode2[tableRow][TableCode.IMA_ORDER]);
							vetImaIncrement.add(tableCode2[tableRow][TableCode.IMA_INCREMENT]);
							vetSpare_1.add(tableCode2[tableRow][TableCode.SPARE_1]);
							vetSpare_2.add(tableCode2[tableRow][TableCode.SPARE_2]);
							vetSpare_3.add(tableCode2[tableRow][TableCode.SPARE_3]);
							vetSerie.add(numSerie);
							vetAcq.add(numAcq);
							vetIma.add(numIma);
							vetAcqTime.add(acqTime);
							vetEchoTime.add(echoTime);
							vetSlicePosition.add(slicePosition);
							vetDirez.add(tableCode2[tableRow][TableCode.DIREZ]);
							vetProfond.add(tableCode2[tableRow][TableCode.PROFOND]);
							vetDone.add(done);
						}
					} else {
						// non aggiunge alcun dato
						// if (questo)
						// MyLog.waitHere("NON leggo questo");
					}
				}
			}
		}

		// a questo punto non mi resta che creare la tabella e riversarvi i dati degli
		// ArrayList
		String[][] tableVuota = TableSequence.createEmptyTable(count3, TableSequence.columns0);

		String[][] tablePass1 = TableSequence.writeColumn(tableVuota, ArrayUtils.arrayListToArrayString(vetConta),
				TableSequence.ROW);
		String[][] tablePass2 = TableSequence.writeColumn(tablePass1, ArrayUtils.arrayListToArrayString(vetPath),
				TableSequence.PATH);
		String[][] tablePass3 = TableSequence.writeColumn(tablePass2, ArrayUtils.arrayListToArrayString(vetCodice),
				TableSequence.CODE);
		String[][] tablePass4 = TableSequence.writeColumn(tablePass3, ArrayUtils.arrayListToArrayString(vetCoil),
				TableSequence.COIL);
		String[][] tablePass5 = TableSequence.writeColumn(tablePass4,
				ArrayUtils.arrayListToArrayString(vetImaDaPassare), TableSequence.IMA_PASS);
		String[][] tablePass6 = TableSequence.writeColumn(tablePass5, ArrayUtils.arrayListToArrayString(vetImaOrder),
				TableSequence.IMA_ORDER);
		String[][] tablePass17 = TableSequence.writeColumn(tablePass6,
				ArrayUtils.arrayListToArrayString(vetImaIncrement), TableSequence.IMA_INCREMENT);
		String[][] tablePass18 = TableSequence.writeColumn(tablePass17, ArrayUtils.arrayListToArrayString(vetSpare_1),
				TableSequence.MULTIPLI);
		String[][] tablePass19 = TableSequence.writeColumn(tablePass18, ArrayUtils.arrayListToArrayString(vetSpare_2),
				TableSequence.SPARE_2);
		String[][] tablePass20 = TableSequence.writeColumn(tablePass19, ArrayUtils.arrayListToArrayString(vetSpare_3),
				TableSequence.SPARE_3);
		String[][] tablePass16 = TableSequence.writeColumn(tablePass20, ArrayUtils.arrayListToArrayString(vetSerie),
				TableSequence.SERIE);
		String[][] tablePass7 = TableSequence.writeColumn(tablePass16, ArrayUtils.arrayListToArrayString(vetAcq),
				TableSequence.ACQ);
		String[][] tablePass8 = TableSequence.writeColumn(tablePass7, ArrayUtils.arrayListToArrayString(vetIma),
				TableSequence.IMA);
		String[][] tablePass9 = TableSequence.writeColumn(tablePass8, ArrayUtils.arrayListToArrayString(vetAcqTime),
				TableSequence.TIME);
		String[][] tablePass10 = TableSequence.writeColumn(tablePass9, ArrayUtils.arrayListToArrayString(vetEchoTime),
				TableSequence.ECHO);
		String[][] tablePass11 = TableSequence.writeColumn(tablePass10,
				ArrayUtils.arrayListToArrayString(vetSlicePosition), TableSequence.POSIZ);
		String[][] tablePass12 = TableSequence.writeColumn(tablePass11, ArrayUtils.arrayListToArrayString(vetDirez),
				TableSequence.DIREZ);
		String[][] tablePass13 = TableSequence.writeColumn(tablePass12, ArrayUtils.arrayListToArrayString(vetProfond),
				TableSequence.PROFOND);
		String[][] tablePass14 = TableSequence.writeColumn(tablePass13, ArrayUtils.arrayListToArrayString(vetDone),
				TableSequence.DONE);
		return tablePass14;
	}

	public static boolean coilPresent(String[] allCoils, String coil) {
		boolean trovato = false;
		for (String coil2 : allCoils) {
			if (coil2.equals(coil)) {
				trovato = true;
			}
		}
//		if (allCoils.length > 1) {
//		MyLog.logVector(allCoils, "allCoils");
//		IJ.log(coil + " " + trovato);
//		MyLog.waitHere();
//		}
		return trovato;
	}

	/**
	 * Espande i codici delle immagini, utilizzando il file expand.txt
	 *
	 * @param codice       codice della misura da espandere
	 * @param eco          tempo di eco della misura da espandere
	 * @param tableExpand4 tabella contenente i dati di expand.txt
	 * @return vettore contenente i dati espansi
	 */
	public String[][] expandCode(String codice, String eco1, String[][] tableExpand4) {

//		MyLog.logMatrix(tableExpand4, "tableExpand");

		if ((codice == null) || (eco1 == null) || (tableExpand4 == null)) {
			MyLog.waitHere();
			return null;
		}

		String eco = eco1.split("\\.")[0];
		// MyLog.waitHere("esamino codice= " + codice + " eco= " + eco);

		List<String> vetOldCode = new ArrayList<String>();
		List<String> vetEcho = new ArrayList<String>();
		List<String> vetNewCode = new ArrayList<String>();
		List<String> vetImaPass = new ArrayList<String>();

		for (int i2 = 0; i2 < tableExpand4.length; i2++) {
			String codiceExpand = TableExpand.getOldCode(tableExpand4, i2);
			String ecoExpand = TableExpand.getEcho(tableExpand4, i2);
//			MyLog.waitHere("ESPANDO codice= " + codice + " codiceExpand= " + codiceExpand + "eco= " + eco + " ecoExpand= "
//			+ ecoExpand);

			if ((codice.equals(codiceExpand)) && (eco.equals(ecoExpand))) {
				vetOldCode.add(tableExpand4[i2][TableExpand.OLD_CODE]);
				vetEcho.add(tableExpand4[i2][TableExpand.ECHO]);
				vetNewCode.add(tableExpand4[i2][TableExpand.NEW_CODE]);
				vetImaPass.add(tableExpand4[i2][TableExpand.IMA_PASS]);

			}
		}
		String[][] out1 = new String[vetOldCode.size()][4];

		for (int i1 = 0; i1 < vetOldCode.size(); i1++) {
			out1[i1][0] = vetOldCode.get(i1);
			out1[i1][1] = vetEcho.get(i1);
			out1[i1][2] = vetNewCode.get(i1);
			out1[i1][3] = vetImaPass.get(i1);
			// IJ.log("-"+i1+"-HO EFFETTUATO oldCode=" + out1[i1][0] + " echo=" +
			// out1[i1][1] + " newCode=" + out1[i1][2] + " ima=" + out1[i1][3]);
		}

		return out1;
	}

	/**
	 * Delete the dot in a string
	 *
	 * @param strIn stringa contenente un numero separato dal punto
	 * @return stringa contenente il numero senza la separazione del punto
	 */
	public String deleteDot(String strIn) {
		String strOut = "";
		int dot = strIn.indexOf('.');
		if (dot > 0) {
			String beforedot = strIn.substring(0, dot);
			String afterdot = strIn.substring(dot + 1, strIn.length());
			strOut = beforedot.concat(afterdot).trim();
		} else {
			strOut = strIn;
		}
		return strOut;
	}

	/**
	 * Lettura di AcqTime di una immagine (Siemens + Philips)
	 *
	 * @param imp1 ImagePlus immagine
	 * @return acqTime
	 */
	public static String readTime(ImagePlus imp1) {
		String acqTime = ReadDicom.readDicomParameter(imp1, MyConst.DICOM_ACQUISITION_TIME);
		if (acqTime.equals("MISSING")) {
			acqTime = ReadDicom.readDicomParameter(imp1, MyConst.DICOM_IMATIME);
		}
		return acqTime;
	}

	/**
	 * Scrive una riga nella tabella dei dati
	 *
	 * @param inTable tabella su cui scrivere
	 * @param vetData dati da scrivere in tabella
	 * @param column  numero della riga in cui scrivere
	 * @return duplicato tabella con scritta la riga
	 */
	public String[][] writeTableColumn(String[][] inTable, List<String> vetData, int column) {
		String[][] outTable = TableSequence.writeColumn(inTable, ArrayUtils.arrayListToArrayString(vetData), column);
		return outTable;
	}

	/**
	 * E' il motore che si preoccupa di chiamare i vari plugin, passando sulla linea
	 * di comando il numero della linea di iw2ayv.txt da analizzare. Tiene conto dei
	 * controlli gia' effettuati, per cui si puo' spegnere il PC e ripartire ad
	 * analizzare dal file successivo all'ultimo analizzato con successo.
	 *
	 * @param tableSequenze5 e' il file iw2ayv.txt ricaricato da disco
	 * @param tableCode5     e' il file codiciXXXX.txt caricato da disco
	 */
	public String[][] callPluginsFromSequenceTable(String[][] tableSequenze5, String[][] tableCode5, boolean test,
			boolean superficiali, boolean p10p11p12, TextWindow tw) {

		// Attenzione: contrariamente a quanto scritto piu' sotto, per
		// la struttura della tableSequenze e' stata creata la classe
		// TableSequence che si occupa di tutto e in cui vengono definiti:
		// ROW = 0, PATH = 1, CODE = 2, COIL = 3, IMA_PASS = 4, SERIE = 5,
		// ACQ = 6, IMA = 7, TIME = 8, ECHO = 9, DONE = 10, COLUMNS = 11
		// facendo riferimento a questi valori si ha il vantaggio che sono
		// definiti in un unico punto. Per la struttura della TableCode � stata
		// creata la classe TableCode che si occupa di tutto e in cui vengono
		// definiti:
		// CODE = 0, IMA_PASS = 1, IMA_TOTAL = 2, COIL = 3, PLUGIN = 4.
		// facendo riferimento a questi valori si ha il vantaggio che vengono
		// definiti in un unico punto.
		//

		// ##########--OBSOLETO-OBSOLETO--OBSOLETO--################
		// OBSOLETO La struttura della tabella tableSequenze � la seguente:
		// OBSOLETO tableSequenze[n][0] = n, contatore progressivo
		// OBSOLETO tableSequenze[n][1] = path file
		// OBSOLETO tableSequenze[n][2] = codice
		// OBSOLETO tableSequenze[n][3] = bobina
		// OBSOLETO tableSequenze[n][4] = immagini da passare ( tabl[w1][1] )
		// OBSOLETO tableSequenze[n][5] = numSerie
		// OBSOLETO tableSequenze[n][6] = numAcq
		// OBSOLETO tableSequenze[n][7] = numIma
		// OBSOLETO tableSequenze[n][8] = acqTime
		// OBSOLETO tableSequenze[n][9] = echoTime
		// OBSOLETO tableSequenze[n][10] = fatto, default 0
		// OBSOLETO La struttura di TableCode � invece quella del file
		// OBSOLETO codici.txt:
		// OBSOLETO tableCode [n][0] = codice
		// OBSOLETO tableCode [n][1] = numero immagini da passare al plugin
		// OBSOLETO tableCode [n][2] = numero immagini totali
		// OBSOLETO tableCode [n][3] = nome del plugin da utilizzare per quel
		// codice
		// ##########--OBSOLETO-OBSOLETO--OBSOLETO--################

		if (tableSequenze5 == null) {
			IJ.log("callPluginsFromSequenceTable riceve NULL : Nessuna immagine da analizzare");
			return null;
		}
		// MyLog.logMatrix(tableSequenze5, "tableSequenze5");
		// MyLog.waitHere();

		int j1 = 0;
		int count = 0;
		List<String> vetPlugin = new ArrayList<String>();
		List<String> vetArgomento = new ArrayList<String>();
		// IJ.log("lunghezza= "+tableSequenze5.length);
		// MyLog.logMatrix(tableSequenze5, "tableSequenze5");
		// MyLog.logMatrix(tableCode5, "tableCode5");
		while (j1 < tableSequenze5.length) {
			// MyLog.waitHere("j1= "+j1);
			if (TableSequence.getDone(tableSequenze5, j1).equals("0")) {
				String plugin = pluginToBeCalledWithCoil(j1, tableSequenze5, tableCode5);
				// qui altero il plugin per poter chiamare, durante i tests le
				// vecchie versioni, senza dover modificare i sorgenti
				if (plugin == null) {
					MyLog.waitHere("plugin == null");
				}

				if (!p10p11p12) {
					// MyLog.waitHere("MANUALE p10p11= " + p10p11);
					if (plugin.equals("contMensili.p19rmn_")) {
						plugin = "contMensili.p5rmn_";
					}
					if (plugin.equals("contMensili.p10rmn_")) {
						plugin = "contMensili.p5rmn_";
					}
					if (plugin.equals("contMensili.p11rmn_")) {
						plugin = "contMensili.p5rmn_";
					}
					if (plugin.equals("contMensili.p12rmn_")) {
						plugin = "contMensili.p3rmn_";
					}
					if (plugin.equals("contMensili.p20rmn_")) {
						plugin = "contMensili.p2rmn_";
					}
					if (plugin.equals("contMensili.p17rmn_")) {
						plugin = "contMensili.p7rmn_";
					}
					if (plugin.equals("contMensili.p16rmn_")) {
						plugin = "contMensili.p6rmn_ORIGINAL";
					}
				}

				// usato durante i test 2024_04_20

//				IJ.log(MyLog.qui() + " SELEZIONE con testP6= " + testP6 + "plugin= " + plugin);
//				MyLog.waitHere();

				if (plugin.equals("contMensili.p6rmn_")) {
					plugin = "contMensili." + choice;
				}

				String argomento = argumentForPluginToBeCalled(j1, tableSequenze5);
				boolean jump = false;
				if (superficiali) {
					if ((plugin.equals("contMensili.p19rmn_")) || (plugin.equals("contMensili.p10rmn_"))
							|| (plugin.equals("contMensili.p11rmn_")) || (plugin.equals("contMensili.p12rmn_"))
							|| (plugin.equals("contMensili.p13rmn_")) || (plugin.equals("contMensili.p5rmn_"))
							|| (plugin.equals("contMensili.p5rmn_"))) {
						jump = false;
						count++;
						// IJ.log("" + count + " eseguito superficiali");
					} else {
						jump = true;
					}
				}
				if ((plugin == null) || (argomento == null) || jump) {
					j1++;
				} else {
					// qui e' dove vengono passate al plugin le righe delle
					// immagini da analizzare.
					new TableSequence();
					int numImaDaPassare = Integer.parseInt(TableSequence.getImaPass(tableSequenze5, j1));

					// MyLog.waitHere();
					String theCode = TableSequence.getCode(tableSequenze5, j1);
					String theCoil = TableSequence.getCoil(tableSequenze5, j1);

					// int numImaGruppo = Integer.parseInt(TableSequence
					// .getImaGroup(tableSequenze5, j1));
					// int numImaGruppo = 0;

					// MyFileLogger.logger.info("<<< RIGA " + j1 + " / "
					// + tableSequenze5.length + " " + theCode + " "
					// + theCoil + " >>>");
					tw.append("<<< RIGA " + j1 + " / " + tableSequenze5.length + " " + theCode + " >><< " + theCoil
							+ " " + plugin + " arg= " + argomento + " >>>");

					if (numImaDaPassare == 0) {
						j1++;
					} else {
						// if (numImaGruppo == 0) {
						// MyFileLogger.logger
						// .info("Sequenze.callPluginFromSequenceTable >>>
						// plugin= "
						// + plugin
						// + " argomento= "
						// + argomento);

						// TODO MA CHE VUOL DIRE, SONO ESATTAMENTE DUE CASI
						// UGUALI UGUALI

						pluginRunner(plugin, argomento, test);
						vetPlugin.add(plugin);
						vetArgomento.add(argomento);
						j1 = j1 + numImaDaPassare;
						// } else {
						// MyFileLogger.logger
						// .info("Sequenze.callPluginFromSequenceTable >>>
						// plugin= "
						// + plugin
						// + " argomento= "
						// + argomento);

						// pluginRunner(plugin, argomento, test);
						// vetPlugin.add(plugin);
						// vetArgomento.add(argomento);
						// j1 = j1 + numImaDaPassare;
						// }
					}
				}
			} else {
				j1++;
			}
		}

		String[][] chiamate = new String[vetPlugin.size()][2];
		for (int i1 = 0; i1 < vetPlugin.size(); i1++) {
			chiamate[i1][0] = vetPlugin.get(i1);
			chiamate[i1][1] = vetArgomento.get(i1);
		}

		// MyLog.here();
		// MyLog.logMatrix(chiamate, "chiamate");
		// MyLog.waitHere();
		return chiamate;

	}

	/***
	 * Effettua il run di un plugin (abbiamo cosi' il plugin sequenze che e' in
	 * grado di chiamare i plugins per le eleborazioni delle varie immagini.
	 *
	 * @param plugin
	 * @param argomento
	 * @param test
	 */
	public void pluginRunner(String plugin, String argomento, boolean test) {
		if (!test) {
			IJ.runPlugIn(plugin, argomento);
		}
	}

	/**
	 * Cerca in tabella il nome del plugin da lanciare per il codice corrispondente
	 * alla linea di ayv.txt elaborata al momento. Potremo avere ad esempio "p6rmn_"
	 *
	 * @param lineNumber    numero linea tableSequenze in elaborazione (la prima con
	 *                      "fatto" =0
	 * @param tableSequenze tabella sequenze in memoria
	 * @param tableCode     tabella codici in memoria
	 * @return nome del plugin da chiamare
	 */
	public String pluginToBeCalled(int lineNumber, String[][] tableSequenze, String[][] tableCode) {
		if ((tableSequenze == null) || (tableCode == null)) {
			return null;
		}
		String nome = null;
		if (TableSequence.getDone(tableSequenze, lineNumber).equals("0")) {
			for (int j2 = 0; j2 < tableCode.length; j2++) {
				if (TableSequence.getCode(tableSequenze, lineNumber).equals(TableCode.getCode(tableCode, j2))) {
					nome = "contMensili." + TableCode.getPluginName(tableCode, j2);
					break;
				}
			}
		}
		return nome;
	}

	/**
	 * Cerca in tabella il nome del plugin da lanciare per il codice corrispondente
	 * alla linea di ayv.txt elaborata al momento. Potremo avere ad esempio
	 * "p6rmn_". Utilizza anche la colonna "COIL"
	 *
	 * @param lineNumber    numero linea tableSequenze in elaborazione (la prima con
	 *                      "fatto" =0
	 * @param tableSequenze tabella sequenze in memoria
	 * @param tableCode     tabella codici in memoria
	 * @return nome del plugin da chiamare
	 */
	public String pluginToBeCalledWithCoil(int lineNumber, String[][] tableSequenze, String[][] tableCode) {
		if (tableSequenze == null) {
			MyLog.waitHere("tableSequenze == null");
			return null;
		}
		if (tableCode == null) {
			MyLog.waitHere("tableCode == null");
			return null;
		}
		String nome = null;
		if (TableSequence.getDone(tableSequenze, lineNumber).equals("0")) {
			for (int j2 = 0; j2 < tableCode.length; j2++) {
				boolean okCode = TableSequence.getCode(tableSequenze, lineNumber)
						.equals(TableCode.getCode(tableCode, j2));
				boolean okCoil = false;
				if (TableCode.getCoil(tableCode, j2).equals("xxx")) {
					okCoil = true;
				} else {
					// in questo modo siamo in grado di confrontare anche bobine
					// multiple accese per errore
					String[] allCoils = ReadDicom.parseString(TableSequence.getCoil(tableSequenze, lineNumber));

					okCoil = coilPresent(allCoils, TableCode.getCoil(tableCode, j2)) || TableSequence
							.getCoil(tableSequenze, lineNumber).equals(TableCode.getCoil(tableCode, j2));
				}
				if (okCode && okCoil) {
					nome = "contMensili." + TableCode.getPluginName(tableCode, j2);
					break;
				}
			}
		}

		if (nome == null) {
			MyLog.waitHere("Code= " + TableSequence.getCode(tableSequenze, lineNumber) + "  Coil= "
					+ TableSequence.getCoil(tableSequenze, lineNumber));
		}

		return nome;
	}

	/**
	 * Cerca in tabella il numero delle linee in cui sono elencate le immagini da
	 * elaborare da parte del plugin e le mette in una stringa da passare come
	 * argomento al plugin. Potremo avere ad esempio 12#13#14#15 , se e' previsto
	 * che quel determinato plugin processi 4 immagini
	 *
	 * @param lineNumber     numero linea tableSequenze in elaborazione (la prima
	 *                       con "fatto" =0
	 * @param tableSequenze5 tabella sequenze in memoria
	 * @return argomento per il plugin da chiamare
	 */
	public String argumentForPluginToBeCalled(int lineNumber, String[][] tableSequenze5) {

		String argomento = "";
		if (tableSequenze5 == null) {
			return null;
		}
		new TableSequence();
		int numImaDaPassare = Integer.parseInt(TableSequence.getImaPass(tableSequenze5, lineNumber));

		// int numImaTab =
		// Integer.parseInt(TableSequence.getImaGroup(tableSequenze5,
		// lineNumber));
		// numImaTab (numero immagine in tabelle) va confrontato col numero
		// immagine, letto dai dati dicom

		if (numImaDaPassare == 0) {
			return null;
		} else {
			// if (numImaGruppo > 0 && numImaDaPassare == 4) {
			// // se numImaGruppo >0 e' il caso della bobina breast, in questo
			// // caso abbiamo 2 immagini successive (i 2 echi), separate da
			// // numImaGruppo dalle restanti
			//
			// new TableSequence();
			// argomento = argomento + "#"
			// + TableSequence.getRow(tableSequenze5, lineNumber + 0);
			// argomento = argomento + "#"
			// + TableSequence.getRow(tableSequenze5, lineNumber + 1);
			// MyLog.waitHere("prima");
			// int num1 = lineNumber + 0 + numImaGruppo;
			// MyLog.waitHere("num1= " + num1 + " lineNumber= " + lineNumber
			// + " numImaGruppo= " + numImaGruppo);
			// argomento = argomento
			// + "#"
			// + TableSequence.getRow(tableSequenze5, lineNumber + 0
			// + numImaGruppo);
			// MyLog.waitHere("dopo");
			// argomento = argomento
			// + "#"
			// + TableSequence.getRow(tableSequenze5, lineNumber + 1
			// + numImaGruppo);
			//
			// } else {
			for (int j2 = 0; j2 < numImaDaPassare; j2++) {
				if ((lineNumber + j2) >= tableSequenze5.length) {
					return null;
				}
				new TableSequence();
				argomento = argomento + "#" + TableSequence.getRow(tableSequenze5, lineNumber + j2);
			}
			// }
		}
		return argomento;
	}

	/**
	 * Verifica che il numero di immagini rilevate da scanlist per un certo tipo di
	 * codice immagine corrisponda al numero previsto in codiciNew.csv. Se cio' non
	 * accade viene passata una tabella di warnings a logVerifySequenceTable. Si
	 * puo' comunque continuare a lavorare
	 *
	 * @param tableSequenze6
	 * @param tableCode6
	 * @return
	 */
	public String[][] verifySequenceTable(String[][] tableSequenze6, String[][] tableCode6) {

		// TODO: incompatibilità HUSA_ vs HUSAA
		// TODO: incompatibilità HUSC_ vs HUSCA
		// TODO: incompatibilità HUSS_ vs HUSSA

		// IJ.log("\\Clear");
		// IJ.log("------- TableSequenze6 ------------");
		// TableUtils.dumpTable(tableSequenze6);
		// IJ.log("------- TableCode6 ------------");
		// TableUtils.dumpTable2(tableCode6);

		if (tableSequenze6 == null) {
			MyLog.here("verifyList2.tableSequenze6 = null");
			return null;
		}
		if (tableCode6 == null) {
			MyLog.here("verifyList2.tableCode6 = null");
			return null;
		}
		List<String> vetCodice = new ArrayList<String>();
		List<String> vetCoil = new ArrayList<String>();
		List<String> vetImaRichieste = new ArrayList<String>();
		List<String> vetImaTrovate = new ArrayList<String>();
		List<String> vetPathImaAcquisite = new ArrayList<String>();
		List<String> vetSerieImaAcquisite = new ArrayList<String>();
		List<String> vetAcqImaAcquisite = new ArrayList<String>();
		List<String> vetImaImaAcquisite = new ArrayList<String>();
		String codiceBobinaAcquisito = "";
		// boolean trigger = true;

		for (int j1 = 0; j1 < tableCode6.length; j1++) {
			int numeroImaAcquisite = 0;
			int numeroImaRichieste = 0;
			if (UtilAyv.isNumeric(TableCode.getImaTotal(tableCode6, j1))) {
				numeroImaRichieste = Integer.parseInt(TableCode.getImaTotal(tableCode6, j1));
			} else {
				// MyLog.waitHere("errore formato a j1= " + j1);
			}
			String codiceImaRichieste = TableCode.getCode(tableCode6, j1);
			if (codiceImaRichieste == null) {
				break;
			}
			if (numeroImaRichieste == 0) {
				continue;
			}
			String codiceBobinaRichiesto = TableCode.getCoil(tableCode6, j1);
			// IJ.log("codiceBobinaRichiesto= "+codiceBobinaRichiesto);
			// codiceBobinaRichiesto = codiceBobinaRichiesto.replaceAll("�",
			// ";");
			// IJ.log("codiceBobinaRichiesto= "+codiceBobinaRichiesto);
			for (int j2 = 0; j2 < tableSequenze6.length; j2++) {
				new TableSequence();
				String codiceImaAcquisite = TableSequence.getCode(tableSequenze6, j2);
				codiceBobinaAcquisito = TableSequence.getCoil(tableSequenze6, j2).replace(';', '+');
				// if (trigger)
				// IJ.log("codiceBobinaAcquisito= " + codiceBobinaAcquisito);

				if (codiceImaAcquisite == null) {
					break;
				}
				if (compareAcqReq(codiceImaAcquisite, codiceImaRichieste, codiceBobinaAcquisito,
						codiceBobinaRichiesto)) {
					// IJ.log("codiceAcquisito=" + codiceBobinaAcquisito
					// + " codiceRichiesto=" + codiceBobinaRichiesto);
					numeroImaAcquisite++;

				}

			}
			// trigger = false;
			if ((numeroImaAcquisite > 0) && (numeroImaAcquisite != numeroImaRichieste)) {
				for (int j3 = 0; j3 < tableSequenze6.length; j3++) {
					new TableSequence();
					String codiceImaAcquisite2 = TableSequence.getCode(tableSequenze6, j3);
					codiceBobinaAcquisito = TableSequence.getCoil(tableSequenze6, j3).replace(';', '+');

					if (codiceImaAcquisite2 == null) {
						break;
					}
					int numeroImaAcquisite2 = 0;

					boolean codiceBobinaOK = false;
					if ((codiceBobinaRichiesto.equals(codiceBobinaAcquisito))
							|| (codiceBobinaRichiesto.equals("xxx"))) {
						codiceBobinaOK = true;
					}

					if (codiceImaAcquisite2.equals(codiceImaRichieste) && (codiceBobinaOK)) {
						numeroImaAcquisite2++;
						vetCodice.add(codiceImaRichieste);
						vetImaRichieste.add("" + numeroImaRichieste);
						vetImaTrovate.add("" + numeroImaAcquisite);
						vetPathImaAcquisite.add(TableSequence.getPath(tableSequenze6, j3));
						vetSerieImaAcquisite.add(TableSequence.getSerie(tableSequenze6, j3));
						vetAcqImaAcquisite.add(TableSequence.getAcq(tableSequenze6, j3));
						vetImaImaAcquisite.add(TableSequence.getIma(tableSequenze6, j3));
						vetCoil.add(TableSequence.getCoil(tableSequenze6, j3));
					}
				}
			}
		}
		// a questo punto non mi resta che creare la tabella e riversarvi i dati
		// dagli ArrayList
		if (vetCodice.size() > 0) {
			String[][] tableVuota = new String[vetCodice.size()][8];
			String[][] writeCol0 = writeTableColumn(tableVuota, vetCodice, TableVerify.CODE);
			String[][] writeCol1 = writeTableColumn(writeCol0, vetImaRichieste, TableVerify.IMA_REQUIRED);
			String[][] writeCol2 = writeTableColumn(writeCol1, vetImaTrovate, TableVerify.IMA_FOUND);
			String[][] writeCol3 = writeTableColumn(writeCol2, vetPathImaAcquisite, TableVerify.PATH);
			String[][] writeCol4 = writeTableColumn(writeCol3, vetSerieImaAcquisite, TableVerify.SERIE);
			String[][] writeCol5 = writeTableColumn(writeCol4, vetAcqImaAcquisite, TableVerify.ACQ);
			String[][] writeCol6 = writeTableColumn(writeCol5, vetImaImaAcquisite, TableVerify.IMA);
			String[][] tableOutput = writeTableColumn(writeCol6, vetCoil, TableVerify.COIL);
			return tableOutput;
		} else {
			return null;
		}
	}

	/**
	 * Verifica che il numero di immagini rilevate da scanlist per un certo tipo di
	 * codice immagine corrisponda al numero previsto in iw2ayv.txt. Se ci� non
	 * accade viene passata una tabella di warnings a logVerifySequenceTable. Si pu�
	 * comunque continuare a lavorare
	 *
	 * @param tableSequenze6
	 * @param tableCode6
	 * @return
	 */
	public String[][] verifySequenceTableOLD(String[][] tableSequenze6, String[][] tableCode6) {

		TableUtils.dumpTable(tableSequenze6);
		TableUtils.dumpTable(tableCode6);

		if (tableSequenze6 == null) {
			MyLog.here("verifyList2.tableSequenze6 = null");
			return null;
		}
		if (tableCode6 == null) {
			MyLog.here("verifyList2.tableCode6 = null");
			return null;
		}
		List<String> vetCodice = new ArrayList<String>();
		List<String> vetCoil = new ArrayList<String>();
		List<String> vetImaRichieste = new ArrayList<String>();
		List<String> vetImaTrovate = new ArrayList<String>();
		List<String> vetPathImaAcquisite = new ArrayList<String>();
		List<String> vetSerieImaAcquisite = new ArrayList<String>();
		List<String> vetAcqImaAcquisite = new ArrayList<String>();
		List<String> vetImaImaAcquisite = new ArrayList<String>();
		for (int j1 = 0; j1 < tableCode6.length; j1++) {
			int numeroImaAcquisite = 0;
			int numeroImaRichieste = Integer.parseInt(TableCode.getImaTotal(tableCode6, j1));
			String codiceImaRichieste = TableCode.getCode(tableCode6, j1);
			if (codiceImaRichieste == null) {
				break;
			}
			if (numeroImaRichieste == 0) {
				continue;
			}
			String codiceBobinaRichiesto = TableCode.getCoil(tableCode6, j1);
			for (int j2 = 0; j2 < tableSequenze6.length; j2++) {
				new TableSequence();
				String codiceImaAcquisite = TableSequence.getCode(tableSequenze6, j2);
				String codiceBobinaAcquisito = TableSequence.getCoil(tableSequenze6, j2);
				if (codiceImaAcquisite == null) {
					break;
				}
				if (compareAcqReq(codiceImaAcquisite, codiceImaRichieste, codiceBobinaAcquisito,
						codiceBobinaRichiesto)) {
					numeroImaAcquisite++;
				}
			}
			if ((numeroImaAcquisite > 0) && (numeroImaAcquisite != numeroImaRichieste)) {
				for (int j3 = 0; j3 < tableSequenze6.length; j3++) {
					new TableSequence();
					String codiceImaAcquisite2 = TableSequence.getCode(tableSequenze6, j3);
					if (codiceImaAcquisite2 == null) {
						break;
					}
					int numeroImaAcquisite2 = 0;
					if (codiceImaAcquisite2.equals(codiceImaRichieste)) {
						numeroImaAcquisite2++;
						vetCodice.add(codiceImaRichieste);
						vetImaRichieste.add("" + numeroImaRichieste);
						vetImaTrovate.add("" + numeroImaAcquisite);
						vetPathImaAcquisite.add(TableSequence.getPath(tableSequenze6, j3));
						vetSerieImaAcquisite.add(TableSequence.getSerie(tableSequenze6, j3));
						vetAcqImaAcquisite.add(TableSequence.getAcq(tableSequenze6, j3));
						vetImaImaAcquisite.add(TableSequence.getIma(tableSequenze6, j3));
						vetCoil.add(TableSequence.getCoil(tableSequenze6, j3));
					}
				}
			}
		}
		// a questo punto non mi resta che creare la tabella e riversarvi i dati
		// dagli ArrayList
		if (vetCodice.size() > 0) {
			String[][] tableVuota = new String[vetCodice.size()][8];
			String[][] writeCol0 = writeTableColumn(tableVuota, vetCodice, TableVerify.CODE);
			String[][] writeCol1 = writeTableColumn(writeCol0, vetImaRichieste, TableVerify.IMA_REQUIRED);
			String[][] writeCol2 = writeTableColumn(writeCol1, vetImaTrovate, TableVerify.IMA_FOUND);
			String[][] writeCol3 = writeTableColumn(writeCol2, vetPathImaAcquisite, TableVerify.PATH);
			String[][] writeCol4 = writeTableColumn(writeCol3, vetSerieImaAcquisite, TableVerify.SERIE);
			String[][] writeCol5 = writeTableColumn(writeCol4, vetAcqImaAcquisite, TableVerify.ACQ);
			String[][] writeCol6 = writeTableColumn(writeCol5, vetImaImaAcquisite, TableVerify.IMA);
			String[][] tableOutput = writeTableColumn(writeCol6, vetCoil, TableVerify.COIL);
			return tableOutput;
		} else {
			return null;
		}
	}

	/***
	 * Verifica che i codici di bobina e sequenza richiesti ed effettivi siano
	 * uguali
	 *
	 * @param codeImaAcq
	 * @param codeImaReq
	 * @param coilImaAcq
	 * @param coilImaReq
	 * @return
	 */
	public boolean compareAcqReq(String codeImaAcq, String codeImaReq, String coilImaAcq, String coilImaReq) {

		boolean res1;
		String[] allCoils = ReadDicom.parseString(coilImaAcq);

		if (codeImaAcq.equals(codeImaReq)) {
			if (coilImaReq.equals("xxx") || coilImaReq.equals("XXX")) {
				res1 = true;
			} else if (coilPresent(allCoils, coilImaReq)) {
				// modifica del 100913 per poter analizzare bobine multiple,
				// attivate per errore da autoselectcoil FALLITA
				// } else if (coilImaAcq.equals(coilImaReq)) {
				res1 = true;
			} else {
				res1 = false;
			}
		} else {
			res1 = false;
		}
		// if (codeImaAcq.equals(codeImaReq)) {
		// MyLog.logVector(allCoils, "allCoils");
		// MyLog.waitHere(codeImaAcq + " " + codeImaReq + " " + coilImaAcq
		// + " " + coilImaReq + " risultato res1= " + res1);
		// }

		return res1;
	}

	public boolean compareAcqReq2(String codeImaAcq, String codeImaReq, String coilImaAcq, String coilImaReq) {

		boolean res1;

		if (codeImaAcq.equals(codeImaReq)) {
			if (coilImaReq.equals("xxx") || coilImaReq.equals("XXX")) {
				res1 = true;
			} else if (coilImaAcq.equals(coilImaReq)) {
				// modifica del 100913 per poter analizzare bobine multiple,
				// attivate per errore da autoselectcoil FALLITA
				// } else if (coilImaAcq.equals(coilImaReq)) {
				res1 = true;
			} else {
				res1 = false;
			}
		} else {
			res1 = false;
		}
		// if (codeImaAcq.equals(codeImaReq)) {
		// MyLog.logVector(allCoils, "allCoils");
		// MyLog.waitHere(codeImaAcq + " " + codeImaReq + " " + coilImaAcq
		// + " " + coilImaReq + " risultato res1= " + res1);
		// }

		return res1;
	}

	/**
	 * Riceve da verifySequenceTable una tabella di codici a cui non corrisponde
	 * l'esatto numero di immagini previste. Stampa a log i warnings.
	 *
	 * @param tableVerify
	 */
	public void logVerifySequenceTable(String[][] tableVerify, boolean test) {

		if (tableVerify == null) {
			return;
		}
		if (!test) {
			IJ.showMessage("Problemi con le immagini, vedere il log");
		}

		// TableUtils.dumpTable(tableVerify, "tableVerify");
		String codice2 = "";
		String coil2 = "";
		boolean stamp = false;
		if (!test) {
			IJ.log("---------------------------------------------");
		}
		for (int i1 = 0; i1 < tableVerify.length; i1++) {
			String codice1 = TableVerify.getCode(tableVerify, i1);
			String coil1 = TableVerify.getCoil(tableVerify, i1);
			int numero = Integer.parseInt(TableVerify.getImaFound(tableVerify, i1));
			if ((!codice1.equals(codice2)) || (!coil1.equals(coil2))) {
				stamp = true;
				if (!test) {
					IJ.log("per il codice " + codice1 + " e la bobina " + coil1 + " sono necessarie "
							+ TableVerify.getImaRequired(tableVerify, i1) + " immagini, trovate " + numero);
				}

				codice2 = codice1;
				coil2 = coil1;
				if ((i1 + numero) <= tableVerify.length) {
					for (int i2 = i1; i2 < i1 + numero; i2++) {

						if (!test) {
							IJ.log(TableVerify.getPath(tableVerify, i2) + " ser:"
									+ TableVerify.getSerie(tableVerify, i2) + " acq:"
									+ TableVerify.getAcq(tableVerify, i2) + " ima:"
									+ TableVerify.getIma(tableVerify, i2) + " coil:"
									+ TableVerify.getCoil(tableVerify, i2));
						}
					}
				}
			}
			if (stamp) {
				if (!test) {
					IJ.log("---------------------------------------------");
				}
				stamp = false;
			}
		}
		if (!test) {
			MyLog.waitHere("Problemi con le immagini, vedere il log");
		}

	}

	/**
	 * Effettua il riordino della tabella dati estraendo il risultato in base
	 * all'ordine di codici.txt
	 *
	 * @param tableSequenze tabella da riordinare
	 * @return tabella riordinata
	 */
	public String[][] reorderSequenceTable2(String[][] tableSequenze, String[][] tableCodici) {

		// MyLog.logMatrix(tableSequenze, "tableSequenze");
		// MyLog.logMatrix(tableCodici, "tableCodici");
		// MyLog.waitHere("salvare il log");

		ArrayList<ArrayList<String>> matrixTableReordered = new ArrayList<ArrayList<String>>();

		if (tableSequenze == null) {
			MyLog.here("reorderSequenceTable.tableSequenze = null");
			return null;
		}
		if (tableCodici == null) {
			MyLog.here("reorderSequenceTable.tableCode = null");
			return null;
		}
		// ora effettuo l'estrazione dei codici dalla tabella, secondo
		// l'ordine di codici.txt
		int count = -1;
		for (String[] element : tableCodici) {
			for (String[] element2 : tableSequenze) {
				if (element2[TableSequence.CODE] == null) {
					IJ.log("reorderSequenceTable.NULL");
				} else if (compareAcqReq(element2[TableSequence.CODE], element[TableCode.CODE],
						element2[TableSequence.COIL], element[TableCode.COIL])
						|| compareAcqReq2(element2[TableSequence.CODE], element[TableCode.CODE],
								element2[TableSequence.COIL], element[TableCode.COIL])) {
					count++;
					ArrayList<String> row = new ArrayList<String>();
					row.add("" + count);
					for (int i3 = 1; i3 < tableSequenze[0].length; i3++) {
						row.add(element2[i3]);
					}
					matrixTableReordered.add(row);
				}
			}
		}
		String[][] tableSequenzeReordered = new InputOutput().fromArrayListToStringTable(matrixTableReordered);
		return (tableSequenzeReordered);
	}

	/**
	 * Effettua il riordino della tabella dati estraendo il risultato in base
	 * all'ordine di codici.txt
	 *
	 * @param tableSequenze tabella da riordinare
	 * @return tabella riordinata
	 */
	public String[][] reorderSequenceTable(String[][] tableSequenze, String[][] tableCodici) {

		// MyLog.logMatrix(tableSequenze, "tableSequenze");
		// MyLog.logMatrix(tableCodici, "tableCodici");
		// MyLog.waitHere("salvare il log");

		ArrayList<ArrayList<String>> matrixTableReordered = new ArrayList<ArrayList<String>>();

		if (tableSequenze == null) {
			MyLog.here("reorderSequenceTable.tableSequenze = null");
			return null;
		}
		if (tableCodici == null) {
			MyLog.here("reorderSequenceTable.tableCode = null");
			return null;
		}
		// ora effettuo l'estrazione dei codici dalla tabella, secondo
		// l'ordine di codici.txt
		int count = -1;
		for (String[] element : tableCodici) {
			for (String[] element2 : tableSequenze) {
				if (element2[TableSequence.CODE] == null) {
					IJ.log("reorderSequenceTable.NULL");
				} else if (compareAcqReq(element2[TableSequence.CODE], element[TableCode.CODE],
						element2[TableSequence.COIL], element[TableCode.COIL])
						|| compareAcqReq2(element2[TableSequence.CODE], element[TableCode.CODE],
								element2[TableSequence.COIL], element[TableCode.COIL])) {
					count++;
					ArrayList<String> row = new ArrayList<String>();
					row.add("" + count);
					for (int i3 = 1; i3 < tableSequenze[0].length; i3++) {
						row.add(element2[i3]);
					}
					matrixTableReordered.add(row);
				}
			}
		}
		String[][] tableSequenzeReordered = new InputOutput().fromArrayListToStringTable(matrixTableReordered);
		return (tableSequenzeReordered);
	}

	public boolean checkSequenceTable(String source) {
		URL url1 = this.getClass().getResource("/" + source);
		if (url1 != null) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Verifica l'esistenza di un file *.jar
	 *
	 * @param source
	 * @return
	 */
	public boolean checkJar(String source) {
		URL url1 = this.getClass().getResource("/" + source);
		if (url1 != null) {
			return true;
		} else {
			return false;
		}
	}

	/***
	 * Questo era un test per vedere se posso avere duefile di identico nome, uno
	 * esterno ed uno interno al file jar (in modo da tenere l'interno come default
	 * ed eventualmente l'esterno come risorsa modificabile dall'utente)
	 *
	 * @param source
	 */

	public void readExperiment(String source) {
		boolean absolute = false;
		if (this.getClass().getResource("/" + source) == null) {
			String[][] mat1 = new InputOutput().readFile6LIKE(source, absolute);
			MyLog.logMatrix(mat1, "mat1");
			MyLog.waitHere();
		} else {
			String[][] mat1 = new InputOutput().readFile6LIKE(source, absolute);
			MyLog.logMatrix(mat1, "mat1");
			MyLog.waitHere();
		}
	}

	/***
	 * Effettua la sostituzione dello 0 utilizzato come wildcard, per indicare che
	 * verranno elaborate tutte le immagini trovate con quel determinato codice.
	 * L'esigenza si e'avuta per la prima volta con la MRI di Treviglio. Per non
	 * dover aggiungere nuovi flag, ho deciso di modificare leggermente l'utilizzo
	 * dei flag ImmaginiDaPassare ed ImmaginiTotali Per il codice HTMA4 Thickness
	 * 2mm Multi Head 0 15 avremo ImmaginiDaPassare 0 ed ImmaginiTotali 15, il nuovo
	 * significato diverra': ImmaginiDaPassare = 0 (che sta per le immagini
	 * effettivamente disponibili) ed ImmaginiTotali 15 sara' il limite massimo del
	 * numero di immagini, oltre cui viene dato il messaggio nel Log (potrebbero
	 * essere state acquisite due volte).
	 *
	 * @param tableSequenze
	 * @return
	 */

	public void treviglioDevelop(String[][] tableSequenze, String[][] tableCode) {

		String codice = "";
		int maximum = 0;
		for (int i2 = 0; i2 < tableCode.length; i2++) {
//			MyLog.waitHere("i2= " + i2 + " code= " + TableCode.getCode(tableCode, i2) + " imaPass= "
//					+ TableCode.getImaPass(tableCode, i2));

			if (Integer.valueOf(TableCode.getImaPass(tableCode, i2)) == 0) {
//				MyLog.waitHere("sono qui allo 0");
				codice = TableCode.getCode(tableCode, i2);
				maximum = Integer.valueOf(TableCode.getImaTotal(tableCode, i2));
			}
//			if (codice.equals("T28_4"))
//				MyLog.waitHere("codice 001=" + codice);
			if (codice.length() < 4) {
				continue;
			}
			int numero = treviglioCountImages(tableSequenze, codice);
//			IJ.log("in treviglioDevelop codice= " + codice + " numero= " + numero);
			treviglioTableSequence(tableSequenze, codice, numero);
			treviglioTableCode(tableCode, codice, numero, maximum);
		}

		return;
	}

	/**
	 * Conta le immagini con un determinato codice presenti nella tableSequenze
	 *
	 * @param tableSequenze
	 * @param codice
	 * @return
	 */
	public int treviglioCountImages(String[][] tableSequenze, String codice) {
		int count = 0;
		for (String[] element : tableSequenze) {
			if (element[TableSequence.CODE].equals(codice)) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Modifica il campo IMA_PASS della tableSequenze per un determinato codice,
	 * inserendovi il numero
	 *
	 * @param tableSequenze
	 * @param codice
	 * @param numero
	 */
	public void treviglioTableSequence(String[][] tableSequenze, String codice, int numero) {
		if (tableSequenze == null) {
			return;
		}

		// altero i dati in tableSequenceLoaded
		for (int i2 = 0; i2 < tableSequenze.length; i2++) {
			if (tableSequenze[i2][TableSequence.CODE].equals(codice)) {
				tableSequenze[i2][TableSequence.IMA_PASS] = Integer.toString(numero);
			}
		}
		return;
	}

	/**
	 * Modifica i campi IMA_PASS della tableCode per un determinato codice
	 * inserendovi il numero. La stessa operazione avviene anche per il campo
	 * IMA_TOTAL a patto che non si superi il maximum
	 *
	 * @param tableCode
	 * @param codice
	 * @param numero
	 * @param maximum
	 */
	public void treviglioTableCode(String[][] tableCode, String codice, int numero, int maximum) {
		if (tableCode == null) {
			return;
		}

		// altero i dati in tableSequenceLoaded
		for (int i2 = 0; i2 < tableCode.length; i2++) {
			if (tableCode[i2][TableCode.CODE].equals(codice)) {
				tableCode[i2][TableCode.IMA_PASS] = Integer.toString(numero);
				if (numero <= maximum) {
					tableCode[i2][TableCode.IMA_TOTAL] = Integer.toString(numero);
				}
			}
		}
		return;
	}

} // ultima
