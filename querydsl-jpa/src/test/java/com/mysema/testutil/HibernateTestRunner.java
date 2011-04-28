/*
 * Copyright (c) 2010 Mysema Ltd.
 * All rights reserved.
 *
 */
package com.mysema.testutil;

import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.rules.MethodRule;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

import com.mysema.query.jpa.domain.Domain;

/**
 * HibernateTestRunner provides.
 *
 * @author tiwe
 * @version $Id$
 */
public class HibernateTestRunner extends BlockJUnit4ClassRunner {

    private SessionFactory sessionFactory;

    public HibernateTestRunner(Class<?> klass) throws InitializationError {
        super(klass);
    }

    @Override
    protected List<MethodRule> rules(Object test) {
        List<MethodRule> rules = super.rules(test);
        rules.add(new MethodRule(){
            @Override
            public Statement apply(final Statement base, FrameworkMethod method, final Object target) {
                return new Statement(){
                    @Override
                    public void evaluate() throws Throwable {
                        Session session = sessionFactory.openSession();
                        target.getClass().getMethod("setSession", Session.class).invoke(target, session);
                        session.beginTransaction();
                        try {
                            base.evaluate();
                        } finally {
                            session.getTransaction().rollback();
                            session.close();    
                        } 
                    }                    
                };
            }
            
        });
        return rules;
    }
    
    @Override
    public void run(final RunNotifier notifier) {
        try {
            Configuration cfg = new Configuration();
            for (Class<?> cl : Domain.classes){
                cfg.addAnnotatedClass(cl);
            }
            HibernateConfig config = getTestClass().getJavaClass().getAnnotation(HibernateConfig.class);
            Properties props = new Properties();
            InputStream is = HibernateTestRunner.class.getResourceAsStream(config.value());
            if (is == null){
                throw new IllegalArgumentException("No configuration available at classpath:" + config.value());
            }
            props.load(is);
            cfg.setProperties(props);
            sessionFactory = cfg.buildSessionFactory();
            super.run(notifier);
        } catch (Exception e) {
            String error = "Caught " + e.getClass().getName();
            throw new RuntimeException(error, e);
        } finally {
            if (sessionFactory != null){
                sessionFactory.close();
            }
        }

    }

}
