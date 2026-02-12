package org.twocache.twocachedemo.service;

import org.twocache.twocachedemo.bean.Customer;

public interface CustomerRepository  extends CrudRepository<Customer, String>{
  //  ScopedValue<Object> findById(String customerId);
}
