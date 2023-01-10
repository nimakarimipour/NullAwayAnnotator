/*
 * MIT License
 *
 * Copyright (c) 2020 Nima Karimipour
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package edu.ucr.cs.riple.core.metadata.index;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import edu.ucr.cs.riple.core.metadata.trackers.Region;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Indexes {@link Error} instances based on the enclosing region. This data structure loads its data
 * from a file at the given path.
 */
public class Index {

  /** Contents of the index. */
  private final Multimap<Region, Error> items;
  /** Factory instance. */
  private final Factory factory;
  /** Paths to the file to load the content from. */
  private final ImmutableSet<Path> paths;

  /**
   * Creates an instance of Index. Contents are accumulated from multiple sources.
   *
   * @param paths ImmutableSet of paths to load the data from. Each file is a TSV file containing
   *     information to create an instance of {@link Error}.
   * @param factory Factory to create instances from file lines.
   */
  public Index(ImmutableSet<Path> paths, Factory factory) {
    this.paths = paths;
    this.items = MultimapBuilder.hashKeys().arrayListValues().build();
    this.factory = factory;
  }

  /** Starts the reading and index process. */
  public void index() {
    items.clear();
    paths.forEach(
        path -> {
          try (BufferedReader br = Files.newBufferedReader(path, UTF_8)) {
            String line = br.readLine();
            // Skip TSV header.
            if (line != null) {
              line = br.readLine();
            }
            while (line != null) {
              Error error = factory.build(line.split("\t"));
              items.put(error.getRegion(), error);
              line = br.readLine();
            }
          } catch (IOException e) {
            throw new RuntimeException("Error happened in indexing", e);
          }
        });
  }

  /**
   * Returns all contents which are enclosed by the given region.
   *
   * @param region Enclosing region.
   * @return Stored contents that are enclosed by the given region.
   */
  public Collection<Error> get(Region region) {
    return items.get(region);
  }

  /**
   * Returns all values.
   *
   * @return Collection of all values.
   */
  public Collection<Error> values() {
    return items.values();
  }

  /**
   * Returns all items regions that holds the given predicate.
   *
   * @param predicate Predicate provided by caller.
   * @return Set of regions.
   */
  public Set<Region> getRegionsOfMatchingItems(Predicate<Error> predicate) {
    return values().stream()
        .filter(predicate)
        .map(t -> t.region)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
