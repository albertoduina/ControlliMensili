package contMensili;

import ij.IJ;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Lo scopo di questa classe è di verificare la presenza di un solo file jar con
 * la parte fissa del nome corrispondente a nome1. questo per prevenire blocchi
 * dei programmi dovuti alla mancanza di un file jar, oppure malfunzionamenti
 * dovuti alla contemporanea presenza di versioni successive del file jar.
 * 
 * @author alberto
 * 
 */
public class Count {

	/**
	 * Conteggio dei files jar con la parte comune del nome uguale.
	 * 
	 * @param nome1
	 *            parte comune del nome
	 * @return true se il conteggio dei files == 1, false se diverso
	 */
	public boolean jarCount(String nome1) {
		List<File> lista1 = listJars2(new File(ij.Menus.getPlugInsPath()));
		String[] list = new String[lista1.size()];
		int j1 = 0;
		for (File file : lista1) {
			list[j1++] = file.getName();
		}
		int count = 0;
		for (File file : lista1) {
			String str = file.getName();
			if (str.startsWith(nome1)) {
				count++;
			}
		}
		String msg = "";
		if (count == 0) {
			IJ.error("ATTENZIONE, manca il file " + nome1 + "xxx.jar");
		}
		if (count > 1) {
			for (File file : lista1) {
				String str = file.getName();
				if (str.startsWith(nome1)) {
					msg = msg + "\n" + file.getPath();
				}
			}
			IJ.error("ATTENZIONE, si vedono versioni multiple del file "
					+ nome1 + "xxx.jar" + msg);
		}
		if (count == 1)
			return true;
		else
			return false;
	}

	public List<File> listJars2(File startingDir) {
		List<File> result = new ArrayList<File>();
		File[] filesAndDirs = startingDir.listFiles();
		if (filesAndDirs == null)
			return null;
		List<File> filesDirs = Arrays.asList(filesAndDirs);
		for (File file : filesDirs) {
			if (!file.isFile()) {
				List<File> deeperList = listJars2(file);
				result.addAll(deeperList);
			} else {
				if (file.getName().endsWith(".jar")) {
					result.add(file);
				}
			}
		}
		return result;
	}

}
