package cn.edu.thssdb.query;

import cn.edu.thssdb.index.BPlusTree;
import cn.edu.thssdb.schema.Row;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Designed for the select query with join/filtering...
 * hasNext() looks up whether the select result contains a next row
 * next() returns a row, plz keep an iterator.
 */

public class QueryTable implements Iterator<Row> {
  private ArrayList<String> columns;
  private String tableName;
  public QueryTable(String tableName, ArrayList<String> columns) {
    // TODO
    this.tableName = tableName;
    this.columns = columns;
  }

  @Override
  public boolean hasNext() {
    // TODO
    return true;
  }

  @Override
  public Row next() {
    // TODO
    return null;
  }
}