package de.abbaddie.rirc

import java.io.File


trait BackupFileProvider {
	def filename : String

	def getFile(write : Boolean = true) : File = {
		val file2 = new File(filename)
		if(write) {
			val file1 = new File(filename + "~")
			if(file1.exists()) file1.delete()

			if(file2.exists()) file2.renameTo(file1)
		}

		if(!file2.exists()) file2.createNewFile()

		file2
	}
}
