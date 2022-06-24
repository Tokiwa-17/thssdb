package cn.edu.thssdb.query;

<<<<<<< HEAD
import cn.edu.thssdb.index.BPlusTree;
import cn.edu.thssdb.schema.Row;
=======
import cn.edu.thssdb.common.Pair;
import cn.edu.thssdb.parser.ImpVisitor;
import cn.edu.thssdb.parser.SQLParser;
import cn.edu.thssdb.schema.Cell;
import cn.edu.thssdb.schema.Column;
import cn.edu.thssdb.schema.Row;
import cn.edu.thssdb.schema.Table;
>>>>>>> ba0c782aca6610fe9091187e6b4e6519d2f8136c

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Designed for the select query with join/filtering...
 * hasNext() looks up whether the select result contains a next row
 * next() returns a row, plz keep an iterator.
 */

<<<<<<< HEAD
public class QueryTable implements Iterator<Row> {
  private ArrayList<String> columns;
  private String tableName;
  public QueryTable(String tableName, ArrayList<String> columns) {
    // TODO
    this.tableName = tableName;
    this.columns = columns;
  }
=======
public class QueryTable implements Iterable<Row> {

  public ArrayList<Column> columns;
  public ArrayList<Row> rows;
>>>>>>> ba0c782aca6610fe9091187e6b4e6519d2f8136c

  public QueryTable(Table table) {
    this.columns = new ArrayList<>();
    for (Column column : table.columns) {
      Column new_column = new Column(table.tableName + "." + column.getColumnName(),
              column.getColumnType(), column.getPrimary(), column.cantBeNull(), column.getMaxLength());
      this.columns.add(new_column);
    }
    Iterator<Row> rowIterator = table.iterator();
    this.rows = ImpVisitor.getRowsSatisfyWhereClause(rowIterator, columns, null);
  }

  public QueryTable(QueryTable left_table, QueryTable right_table, SQLParser.ConditionContext joinCondition) {
    (this.columns = new ArrayList<>(left_table.columns)).addAll(right_table.columns);
    this.rows = new ArrayList<>();

    String leftColumnName = null, rightColumnName = null;
    int leftColumnIndex = -1, rightColumnIndex = -1;

    if (joinCondition != null) {
      leftColumnName = joinCondition.expression(0).getText().toLowerCase();
      rightColumnName = joinCondition.expression(1).getText().toLowerCase();
      leftColumnIndex = ImpVisitor.getIndexOfAttrName(left_table.columns, leftColumnName);
      rightColumnIndex = ImpVisitor.getIndexOfAttrName(right_table.columns, rightColumnName);
    }

    if (leftColumnIndex == -1 || rightColumnIndex == -1) {
//      throw new Exception("doesn't have the attribute at ON clause");
    }

    for (Row left_row : left_table.rows) {
      for (Row right_row : right_table.rows) {
        if (joinCondition != null) {
          Cell leftRefValue = left_row.getEntries().get(leftColumnIndex);
          Cell rightRefValue = right_row.getEntries().get(rightColumnIndex);
          if (leftRefValue.equals(rightRefValue) == false) {
            continue;
          }
        }
        Row new_row = new Row(left_row);
        new_row.getEntries().addAll(right_row.getEntries());
        this.rows.add(new_row);
      }
    }
  }
  @Override
  public Iterator<Row> iterator() {
    return rows.iterator();
  }
}