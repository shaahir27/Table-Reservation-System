package dao;

import java.util.List;
import java.util.Optional;

/**
 * Generic Data Access Object interface showcasing OOP polymorphism and generics.
 *
 * @param <T>  Domain entity type
 * @param <ID> Primary identifier type
 */
public interface CrudDAO<T, ID> {
    /**
     * Inserts a new entity and updates its generated identifier.
     */
    void add(T entity);

    /**
     * Retrieves an entity by its primary key.
     */
    Optional<T> getById(ID id);

    /**
     * Retrieves all entities ordered by a default sort key.
     */
    List<T> getAll();

    /**
     * Updates an existing entity's details.
     */
    void update(T entity);

    /**
     * Deletes an entity by its primary key.
     */
    void delete(ID id);
}
