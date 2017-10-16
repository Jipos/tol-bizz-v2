package be.kuleuven.toledo.tolbizzv2

import org.springframework.batch.item.ExecutionContext
import org.springframework.batch.item.excel.Sheet
import org.springframework.batch.item.excel.mapping.BeanWrapperRowMapper
import org.springframework.batch.item.excel.poi.PoiItemReader
import org.springframework.batch.item.excel.support.rowset.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.io.Closeable
import java.util.*

class BatchEnrollDto {
    lateinit var userId: String
    var learningUnitId: Double = 0.0
    lateinit var role: Date
    override fun toString() =
            "BatchEnrollD[userId=$userId, learningUnitId=$learningUnitId, role=$role]"
}

class NonEmptyRowNumberColumnNameExtractor(val headerRowNumber: Int = 0) : ColumnNameExtractor {

    override fun getColumnNames(sheet: Sheet) =
            sheet.getRow(headerRowNumber)
                    .filter { it.isNotBlank() }
                    .toTypedArray()

}

class CustomRowSetMetaData(private val sheet: Sheet) : RowSetMetaData {

    lateinit var columnNameExtractor: ColumnNameExtractor

    override fun getColumnNames() = columnNameExtractor.getColumnNames(sheet)

    override fun getColumnName(idx: Int) = columnNames[idx]

    override fun getColumnCount() = sheet.numberOfColumns

    override fun getSheetName() = sheet.name

}

class CustomRowSet(private val sheet: Sheet, private val metaData: CustomRowSetMetaData, private val ignoreEmptyRows: Boolean = false) : RowSet {

    private var currentRowIndex = -1
    private var currentRow: Array<String>? = null

    override fun next(): Boolean {
        currentRow = null
        currentRowIndex++
        if (currentRowIndex < sheet.numberOfRows) {
            currentRow = sheet.getRow(currentRowIndex)
            return if (ignoreEmptyRows && currentRow!!.all { it.isEmpty() }) next() else true
        }
        return false
    }

    override fun getMetaData() = metaData

    override fun getCurrentRowIndex() = currentRowIndex

    override fun getCurrentRow() = currentRow

    override fun getColumnValue(idx: Int) = currentRow!![idx]

    override fun getProperties(): Properties {
        val names = metaData.columnNames ?: throw IllegalStateException("Cannot create properties without meta data")

        val props = Properties()
        for (i in names.indices) {
            val value = currentRow!![i]
            if (value != null) {
                props.setProperty(names[i], value)
            }
        }
        return props
    }
}

class CustomRowSetFactory(private val columnNameExtractor: ColumnNameExtractor = RowNumberColumnNameExtractor()) : RowSetFactory {

    override fun create(sheet: Sheet): RowSet {
        val metaData = CustomRowSetMetaData(sheet)
        metaData.columnNameExtractor = columnNameExtractor
        return CustomRowSet(sheet, metaData, true)
    }

}

@Configuration
class ExcelConfig {

    @Bean
    fun excelItemReader() =
        PoiItemReader<BatchEnrollDto>().apply {
            setResource(excelFile())
            setLinesToSkip(1)
            setRowMapper(excelRowMapper())
            setRowSetFactory(rowSetFactory())
        }

    private fun excelFile() = ClassPathResource("batchEnrollTest2.xlsx")

    private fun rowSetFactory() =
            CustomRowSetFactory(NonEmptyRowNumberColumnNameExtractor())

    private fun excelRowMapper() =
        BeanWrapperRowMapper<BatchEnrollDto>().apply {
            setTargetType(BatchEnrollDto::class.java)
            setStrict(false)
        }

}

@RestController
class ExcelDController(val excelItemReader: PoiItemReader<BatchEnrollDto>) {

    @GetMapping("/excel")
    fun excel(): String {
        ExcelReader(excelItemReader).use { reader ->
            reader.forEach { dto ->
                println(dto)
            }
        }

        return "finished"
    }

}

class ExcelReader<out T>(private val excelItemReader: PoiItemReader<T>): Closeable, Iterable<T> {

    init {
        excelItemReader.open(ExecutionContext())
    }

    override fun close() {
        excelItemReader.close()
    }

    override fun iterator() =
            ExcelItemReaderIterator(excelItemReader)

    inner class ExcelItemReaderIterator(excelItemReader: PoiItemReader<T>): Iterator<T> {

        private var next: T? = excelItemReader.read()

        override fun hasNext() = next != null

        override fun next(): T {
            val result = next
            next = excelItemReader.read()
            return result!!
        }

    }
}