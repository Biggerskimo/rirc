package de.abbaddie.rirc

import java.io.File
import java.nio.file.Files


trait BackupFileProvider {
	def filename : String

	def getFile(write : Boolean = true, append : Boolean = false) : File = {
		val file2 = new File(filename)
		if(write) {
			val file1 = new File(filename + "~")
			if(file1.exists()) file1.delete()

			if(file2.exists()) {
				if(!append) file2.renameTo(file1)
				else Files.copy(file2.toPath, file1.toPath)
			}
		}

		if(!file2.exists()) file2.createNewFile()

		file2
	}
}
