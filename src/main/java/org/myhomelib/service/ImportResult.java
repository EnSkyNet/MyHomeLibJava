package org.myhomelib.service;

import org.myhomelib.model.Fb2Book;

import java.util.List;

public record ImportResult(List<Fb2Book> scannedBooks, int savedBooks) {
}
