package cn.edu.thssdb.parser;


// TODO: add logic for some important cases, refer to given implementations and SQLBaseVisitor.java for structures

import cn.edu.thssdb.exception.DatabaseNotExistException;
import cn.edu.thssdb.exception.SchemaLengthMismatchException;
import cn.edu.thssdb.exception.TableNotExistException;
import cn.edu.thssdb.query.QueryResult;
import cn.edu.thssdb.query.QueryTable;
import cn.edu.thssdb.schema.*;
import cn.edu.thssdb.type.ColumnType;

import java.util.*;

import static cn.edu.thssdb.schema.Column.parseEntry;


/**
 * When use SQL sentence, e.g., "SELECT avg(A) FROM TableX;"
 * the parser will generate a grammar tree according to the rules defined in SQL.g4.
 * The corresponding terms, e.g., "select_stmt" is a root of the parser tree, given the rules
 * "select_stmt :
 *     K_SELECT ( K_DISTINCT | K_ALL )? result_column ( ',' result_column )*
 *         K_FROM table_query ( ',' table_query )* ( K_WHERE multiple_condition )? ;"
 *
 * This class "ImpVisit" is used to convert a tree rooted at e.g. "select_stmt"
 * into the collection of tuples inside the database.
 *
 * We give you a few examples to convert the tree, including create/drop/quit.
 * You need to finish the codes for parsing the other rooted trees marked TODO.
 */

public class ImpVisitor extends SQLBaseVisitor<Object> {
    private Manager manager;
    private long session;

    public ImpVisitor(Manager manager, long session) {
        super();
        this.manager = manager;
        this.session = session;
    }

    private Database GetCurrentDB() {
        Database currentDB = manager.getCurrentDatabase();
        if(currentDB == null) {
            throw new DatabaseNotExistException();
        }
        return currentDB;
    }

    public QueryResult visitSql_stmt(SQLParser.Sql_stmtContext ctx) {
        if (ctx.create_db_stmt() != null) return new QueryResult(visitCreate_db_stmt(ctx.create_db_stmt()));
        if (ctx.drop_db_stmt() != null) return new QueryResult(visitDrop_db_stmt(ctx.drop_db_stmt()));
        if (ctx.use_db_stmt() != null)  return new QueryResult(visitUse_db_stmt(ctx.use_db_stmt()));
        if (ctx.create_table_stmt() != null) return new QueryResult(visitCreate_table_stmt(ctx.create_table_stmt()));
        if (ctx.drop_table_stmt() != null) return new QueryResult(visitDrop_table_stmt(ctx.drop_table_stmt()));
        if (ctx.insert_stmt() != null) return new QueryResult(visitInsert_stmt(ctx.insert_stmt()));
        if (ctx.delete_stmt() != null) return new QueryResult(visitDelete_stmt(ctx.delete_stmt()));
        if (ctx.update_stmt() != null) return new QueryResult(visitUpdate_stmt(ctx.update_stmt()));
        if (ctx.select_stmt() != null) return visitSelect_stmt(ctx.select_stmt());
        if (ctx.quit_stmt() != null) return new QueryResult(visitQuit_stmt(ctx.quit_stmt()));
        if (ctx.show_meta_stmt() != null) return new QueryResult(visitShow_meta_stmt(ctx.show_meta_stmt()));
        return null;
    }

    /**
     创建数据库
     */
    @Override
    public String visitCreate_db_stmt(SQLParser.Create_db_stmtContext ctx) {
        try {
            manager.createDatabaseIfNotExists(ctx.database_name().getText().toLowerCase());
            manager.persist();
        } catch (Exception e) {
            return e.getMessage();
        }
        return "Create database " + ctx.database_name().getText() + ".";
    }

    /**
     删除数据库
     */
    @Override
    public String visitDrop_db_stmt(SQLParser.Drop_db_stmtContext ctx) {
        try {
            manager.deleteDatabase(ctx.database_name().getText().toLowerCase());
        } catch (Exception e) {
            return e.getMessage();
        }
        return "Drop database " + ctx.database_name().getText() + ".";
    }

    /**
     切换数据库
     */
    @Override
    public String visitUse_db_stmt(SQLParser.Use_db_stmtContext ctx) {
        try {
            manager.switchDatabase(ctx.database_name().getText().toLowerCase());
        } catch (Exception e) {
            return e.getMessage();
        }
        return "Switch to database " + ctx.database_name().getText() + ".";
    }

    /**
     删除表格
     */
    @Override
    public String visitDrop_table_stmt(SQLParser.Drop_table_stmtContext ctx) {
        try {
            GetCurrentDB().drop(ctx.table_name().getText().toLowerCase());
        } catch (Exception e) {
            return e.getMessage();
        }
        return "Drop table " + ctx.table_name().getText() + ".";
    }

    public Object visitParse(SQLParser.ParseContext ctx) {
        return visitSql_stmt_list(ctx.sql_stmt_list());
    }

    public Object visitSql_stmt_list(SQLParser.Sql_stmt_listContext ctx) {
        ArrayList<QueryResult> ret = new ArrayList<>();
        for (SQLParser.Sql_stmtContext subCtx : ctx.sql_stmt()) ret.add(visitSql_stmt(subCtx));
        return ret;
    }
    /**
     * TODO
     展示表格项
     show table tablename;
     */
    @Override
    public String visitShow_meta_stmt(SQLParser.Show_meta_stmtContext ctx) {
        try {
            String tableName = ctx.table_name().children.get(0).toString().toLowerCase(Locale.ROOT);
            Database currentDB = manager.getCurrentDatabase();
            Table table = currentDB.get(tableName);
            ArrayList<Column> columns = new ArrayList<Column>();
            int columnNum = table.columns.size();
            String output = "table " + tableName + "\n";
            for (int i = 0; i < columnNum; i++) {
                columns.add(table.columns.get(i));
            }
            for (int i = 0; i < columnNum; i++) {
                Column column = columns.get(i);
                output = output + column.getColumnName().toString().toLowerCase(Locale.ROOT) + "(" + column.getMaxLength() + ")" + "\t" + column.getColumnType().toString().toUpperCase(Locale.ROOT) + "\t";
                if (columns.get(i).isPrimary()) {
                    output = output + "PRIMARY KEY\t";
                }
                if (columns.get(i).cantBeNull()) {
                    output = output + "NOT NULL";
                }
                output += "\n";
            }
            return output + "\n";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    /**
     * TODO
     创建表格
     CREATE TABLE tableName(attrName1 Type1, attrName2 Type2,…, attrNameN TypeN NOT NULL, PRIMARY KEY(attrName1))
     */
    @Override
    public String visitCreate_table_stmt(SQLParser.Create_table_stmtContext ctx) {
        try {
            String tablename = ctx.table_name().children.get(0).toString(); // create table tablename 第2个是tablename
            int n = ctx.getChildCount();
            ArrayList<Column> columnItems = new ArrayList<>();
            for(int i = 4; i < n; i += 2) { //对每个数据项的type进行分析
                if(ctx.getChild(i).getClass().getName().equals("cn.edu.thssdb.parser.SQLParser$Column_defContext")) {
                    String columnName = ((SQLParser.Column_defContext)ctx.getChild(i)).column_name().children.get(0).toString().toLowerCase(Locale.ROOT);
                    String typeName = ((SQLParser.Column_defContext)ctx.getChild(i)).type_name().children.get(0).toString();
                    ColumnType type = ColumnType.INT;
                    if(typeName.toLowerCase().equals("int")) {
                        type = ColumnType.INT;
                    } else if (typeName.toLowerCase().equals("long")) {
                        type = ColumnType.LONG;
                    } else if (typeName.toLowerCase().equals("float")) {
                        type = ColumnType.FLOAT;
                    } else if (typeName.toLowerCase().equals("double")) {
                        type = ColumnType.DOUBLE;
                    } else if (typeName.toLowerCase().equals("string")) {
                        type = ColumnType.STRING;
                    }
                    int length = 128;
                    try {
                        length = Integer. parseInt(((SQLParser.Column_defContext)ctx.getChild(i)).type_name().children.get(2).toString());
                    } catch(Exception e) {

                    }
                    Boolean notNull = false;
                    int constraint_num = ((SQLParser.Column_defContext)ctx.getChild(i)).column_constraint().size();
                    for (int j = 0; j < constraint_num; j++) {
                        if (((SQLParser.Column_defContext)ctx.getChild(i)).column_constraint(j).children.get(0).toString().toLowerCase().equals("not") &&
                                ((SQLParser.Column_defContext)ctx.getChild(i)).column_constraint(j).children.get(1).toString().toLowerCase().equals("null")) {
                            notNull = true;
                        }
                    }
                    columnItems.add(new Column(columnName, type, 0, notNull, length));
                    //columnItems.add((ColumnItem(columnName, typeName, false, false));
                } else {
                    if (((SQLParser.Table_constraintContext)ctx.getChild(i)).children.get(0).toString().toLowerCase().equals("primary") &&
                            ((SQLParser.Table_constraintContext)ctx.getChild(i)).children.get(1).toString().toLowerCase().equals("key")) {
                        String columnName = ((SQLParser.Column_nameContext)(((SQLParser.Table_constraintContext)ctx.getChild(i)).children.get(3))).children.get(0).toString().toLowerCase(Locale.ROOT);
                        int columnNum = columnItems.size();
                        for(int j = 0; j < columnNum; j++) {
                            if(columnItems.get(j).getColumnName().equals(columnName)) {
                                columnItems.get(j).setPrimary(1);
                            }
                        }
                    }
                }
            }
            Column[] columns = new Column[columnItems.size()];
            for (int i = 0; i < columnItems.size(); i++) {
                columns[i] = columnItems.get(i);
            }
            GetCurrentDB().create(tablename, columns);
            return "create table " + tablename + " successful";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    /**
     * TODO
     表格项插入
     */
    @Override
    public String visitInsert_stmt(SQLParser.Insert_stmtContext ctx) {
        try {
            //从sql语句解析
            String tableName = ctx.table_name().children.get(0).toString();
            List<SQLParser.Column_nameContext> columnName = ctx.column_name();
            List<SQLParser.Value_entryContext> values = ctx.value_entry();

            Database database = Manager.getInstance().getCurrentDatabase();
            Table table = database.get(tableName);
            ArrayList<Column> columns = table.columns;

            if(columnName.size() == 0) {
                for (SQLParser.Value_entryContext value: values) {
                    if (value.literal_value().size() != columns.size()) {
                        throw new SchemaLengthMismatchException(columns.size(),value.literal_value().size(),"wrong insert operation (columns unmatched)!");
                    }
                    ArrayList<Cell> cells = new ArrayList<>();

                    for (int i = 0; i < columns.size(); i++){
                        cells.add(parseEntry(value.literal_value(i).getText(), columns.get(i))) ;
                    }
                    Row newRow = new Row(cells);
                    table.insert(newRow);
                }
            }
            else {
                ArrayList<Integer> columnMatch = new ArrayList<>();
                for(int i = 0 ; i < columnName.size(); i++){
                    for (int j = 0; j < columns.size(); j++){
                        if (columnName.get(i).getText().equals(columns.get(j).getColumnName())) {
                            columnMatch.add(j);
                            break;
                        }
                    }
                }
                for (SQLParser.Value_entryContext value: values) {
                    if (value.literal_value().size() != columnName.size()) {
                        throw new SchemaLengthMismatchException(columnName.size(),value.literal_value().size(),"wrong insert operation (columns unmatched)!");
                    }
                    ArrayList<Cell> cells = new ArrayList<>();

                    for (int i = 0; i < columnName.size(); i++){
                        cells.add(parseEntry(value.literal_value(i).getText(), columns.get(columnMatch.get(i)))) ;
                    }
                    Row newRow = new Row(cells);
                    table.insert(newRow);
                }
            }

            return "insert successful";

        }catch (Exception e){
            return e.getMessage();
        }
    }

    /**
     * TODO
     表格项删除
     */
    @Override
    public String visitDelete_stmt(SQLParser.Delete_stmtContext ctx) {
        try {
            //从sql语句解析
            String tableName = ctx.table_name().children.get(0).toString();


            Database database = Manager.getInstance().getCurrentDatabase();
            Table table = database.get(tableName);
            ArrayList<Column> columns = table.columns;

            Iterator<Row> rowIterator = table.iterator();
//            SQLParser.ConditionContext whereItem = ctx.multiple_condition().condition();
            if (ctx.K_WHERE() == null) {
                while(rowIterator.hasNext()){
                    Row row = rowIterator.next();
                    table.delete(row);
                }
            } else {
                String attrName = ctx.multiple_condition().condition().expression(0).comparer().column_full_name().column_name().getText().toLowerCase();
                String attrValue = ctx.multiple_condition().condition().expression(1).comparer().literal_value().getText();
                SQLParser.ComparatorContext comparator = ctx.multiple_condition().condition().comparator();
                int columnIndex = -1;
                for (int j = 0; j < columns.size(); j++){
                    if (attrName.equals(columns.get(j).getColumnName())) {
                        columnIndex = j;
                        break;
                    }
                }
                Cell compareValue = parseEntry(attrValue, columns.get(columnIndex));
                while (rowIterator.hasNext()){
                    Row row = rowIterator.next();
                    Cell columnValue = row.getEntries().get(columnIndex);
                    if(comparator.LT() != null){
                        if(columnValue.compareTo(compareValue) < 0)
                            table.delete(row);
                    }else if(comparator.GT() != null){
                        if (columnValue.compareTo(compareValue) > 0)
                            table.delete(row);
                    }else if(comparator.LE() != null){
                        if (columnValue.compareTo(compareValue) <= 0)
                            table.delete(row);
                    }else if(comparator.GE() != null){
                        if (columnValue.compareTo(compareValue) >= 0)
                            table.delete(row);
                    }else if(comparator.EQ() != null){
                        if (columnValue.compareTo(compareValue) == 0)
                            table.delete(row);
                    }else if(comparator.NE() != null){
                        if (columnValue.compareTo(compareValue) != 0)
                            table.delete(row);
                    }
                }
            }
            return "delete successfully";
        }catch (Exception e){
            return e.getMessage();
        }

    }

    /**
     * TODO
     表格项更新
     */
    public static int getIndexOfAttrName (ArrayList<Column> columns, String AttrName) {
        for (int i = 0; i < columns.size(); ++i) {
            if (columns.get(i).getColumnName().equals(AttrName)) {
                return i;
            }
        }
        return -1;
    }

    public static ArrayList<Row> getRowsSatisfyWhereClause (Iterator<Row> rowIterator,  ArrayList<Column> columns, SQLParser.ConditionContext updateCondition) {
        String attrName = null;
        String attrValue = null;
        int attrIndex = 0;
        SQLParser.ComparatorContext comparator = null;
        Cell compareValue = null;
        ArrayList<Row>rows = new ArrayList<Row>();

        if (updateCondition != null) {
            attrName = updateCondition.expression(0).comparer().column_full_name().column_name().getText().toLowerCase();
            attrValue = updateCondition.expression(1).comparer().literal_value().getText();
            attrIndex = getIndexOfAttrName(columns, attrName);
            comparator = updateCondition.comparator();
            compareValue = parseEntry(attrValue, columns.get(attrIndex));
        }

        while (rowIterator.hasNext()){
            Row row = rowIterator.next();
            Cell columnValue = row.getEntries().get(attrIndex);
            boolean flag = false;
            if(comparator == null) {
                flag = true;
            } else if (comparator.LT() != null) {
                if (columnValue.compareTo(compareValue) < 0)
                    flag = true;
            } else if(comparator.GT() != null) {
                if (columnValue.compareTo(compareValue) > 0)
                    flag = true;
            } else if(comparator.LE() != null) {
                if (columnValue.compareTo(compareValue) <= 0)
                    flag = true;
            } else if(comparator.GE() != null) {
                if (columnValue.compareTo(compareValue) >= 0)
                    flag = true;
            } else if(comparator.EQ() != null) {
                if (columnValue.compareTo(compareValue) == 0)
                    flag = true;
            } else if(comparator.NE() != null) {
                if (columnValue.compareTo(compareValue) != 0)
                    flag = true;
            }
            if (flag) {
                rows.add(row);
            }
        }
        return rows;
    }
    @Override
    public String visitUpdate_stmt(SQLParser.Update_stmtContext ctx) {
        try {
            //从sql语句解析
            String tableName = ctx.table_name().getText();

            Database database = Manager.getInstance().getCurrentDatabase();
            Table table = database.get(tableName);
            Iterator<Row> rowIterator = table.iterator();
            // update table_name SET attrName1 = attrValue1
            String attrName1 = ctx.getChild(3).getText().toLowerCase();
            int attrIndex1 = getIndexOfAttrName(table.columns, attrName1);
            if (attrIndex1 == -1) {
                throw new Exception(tableName + " doesn't have column " + attrName1);
            }

            Cell attrValue1 = parseEntry(ctx.getChild(5).getText(), table.columns.get(attrIndex1));
//            SQLParser.ConditionContext whereItem = ctx.multiple_condition().condition();
            SQLParser.ConditionContext updateCondition = ctx.K_WHERE() == null ? null : ctx.multiple_condition().condition();
            ArrayList<Row> updatedRows = getRowsSatisfyWhereClause(rowIterator, table.columns, updateCondition);

            for (Row row: updatedRows) {
//                System.out.println(row.toString());
                ArrayList<Cell> rowEntries = new ArrayList<Cell>(row.getEntries());
                rowEntries.set(attrIndex1, attrValue1);
                table.update(row.getEntries().get(table.primaryIndex), new Row(rowEntries));
            }

            return "update successfully";
        }catch (Exception e){
            return e.getMessage();
        }
    }

    /**
     * TODO
     表格项查询
     */
    QueryTable getQueryTableFromSingleTable(SQLParser.Table_nameContext ctx) {
        Database database = Manager.getInstance().getCurrentDatabase();
        Table table = database.get(ctx.getText());
        QueryTable queryTable = new QueryTable(table);
        return queryTable;
    }
    QueryTable getQueryTable(SQLParser.Table_queryContext ctx) {
        if (ctx.getChildCount() == 1) {
            return getQueryTableFromSingleTable(ctx.table_name(0));
        }

        SQLParser.Table_queryContext left_query = ctx.table_query();
        QueryTable left_table = null, right_table = null;
        if (left_query == null) {
            left_table = getQueryTableFromSingleTable(ctx.table_name(0));
            right_table = getQueryTableFromSingleTable(ctx.table_name(1));
        } else {
            left_table = getQueryTable(left_query);
            right_table = getQueryTableFromSingleTable(ctx.table_name(0));
        }
        SQLParser.ConditionContext joinCondition = null;
        if (ctx.K_ON() != null) {
            joinCondition = ctx.multiple_condition().condition();
        }
        QueryTable cross_table = new QueryTable(left_table, right_table, joinCondition);
        return cross_table;
    }

    @Override
    public QueryResult visitSelect_stmt(SQLParser.Select_stmtContext ctx) {
        try {
            // 先处理from子句
            List<SQLParser.Table_queryContext> querys = ctx.table_query();
            // 有多个逗号隔开的table,先分别算出每一个（目前认为只有一个）
            QueryTable queryResult = null;
            for(SQLParser.Table_queryContext query : querys) {
                if (queryResult == null) {
                    queryResult = getQueryTable(query);
                } else {
                    queryResult = new QueryTable(queryResult, getQueryTable(query), null);
                }
            }
            // 处理where子句
            if (ctx.K_WHERE() != null) {
                SQLParser.ConditionContext selectCondition = ctx.multiple_condition().condition();
                ArrayList<Row> newRows = getRowsSatisfyWhereClause(queryResult.iterator(), queryResult.columns, selectCondition);
                queryResult.rows = newRows;
            }
            // 处理select子句
            List<SQLParser.Result_columnContext> columnContexts = ctx.result_column();
            ArrayList<Integer> columnIndexs = new ArrayList<>();
            ArrayList<String> finalColumnNames = new ArrayList<>();
            for (SQLParser.Result_columnContext columnContext : columnContexts) {
                String columnName = columnContext.column_full_name().getText().toLowerCase();
                finalColumnNames.add(columnName);
                int index = getIndexOfAttrName(queryResult.columns, columnName);
                columnIndexs.add(index);
            }

            ArrayList<Row> finalRows = new ArrayList<>();
            for (Row row : queryResult.rows) {
                ArrayList<Cell> finalRowEntries = new ArrayList<>();
                for (int index : columnIndexs) {
                    finalRowEntries.add(row.getEntries().get(index));
                }
                finalRows.add(new Row(finalRowEntries));
            }

            return new QueryResult(finalRows, finalColumnNames);
        } catch (Exception e){
            return new QueryResult(e.getMessage());
        }
    }

    /**
     退出
     */
    @Override
    public String visitQuit_stmt(SQLParser.Quit_stmtContext ctx) {
        try {
            manager.quit();
        } catch (Exception e) {
            return e.getMessage();
        }
        return "Quit.";
    }
}
