package org.apache.aries.tx.control.jpa.xa.eclipse.impl;

import static javax.transaction.Status.STATUS_ACTIVE;
import static javax.transaction.Status.STATUS_COMMITTED;
import static javax.transaction.Status.STATUS_COMMITTING;
import static javax.transaction.Status.STATUS_MARKED_ROLLBACK;
import static javax.transaction.Status.STATUS_NO_TRANSACTION;
import static javax.transaction.Status.STATUS_PREPARING;
import static javax.transaction.Status.STATUS_ROLLEDBACK;
import static javax.transaction.Status.STATUS_ROLLING_BACK;
import static javax.transaction.Status.STATUS_UNKNOWN;
import static org.osgi.service.transaction.control.TransactionStatus.ACTIVE;
import static org.osgi.service.transaction.control.TransactionStatus.COMMITTED;
import static org.osgi.service.transaction.control.TransactionStatus.NO_TRANSACTION;
import static org.osgi.service.transaction.control.TransactionStatus.PREPARING;
import static org.osgi.service.transaction.control.TransactionStatus.ROLLED_BACK;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.xa.XAResource;

import org.eclipse.persistence.internal.sessions.AbstractSession;
import org.eclipse.persistence.internal.sessions.UnitOfWorkImpl;
import org.eclipse.persistence.platform.server.ServerPlatformBase;
import org.eclipse.persistence.sessions.DatabaseSession;
import org.eclipse.persistence.transaction.AbstractSynchronizationListener;
import org.eclipse.persistence.transaction.AbstractTransactionController;
import org.eclipse.persistence.transaction.SynchronizationListenerFactory;
import org.osgi.service.transaction.control.TransactionContext;
import org.osgi.service.transaction.control.TransactionControl;
import org.osgi.service.transaction.control.TransactionException;
import org.osgi.service.transaction.control.TransactionStatus;

public class EclipseTxControlPlatform extends ServerPlatformBase {

	public static void setTransactionControl(TransactionControl txControl) {
		TxControlAdapter.txControl = txControl;
	}
	
	public EclipseTxControlPlatform(DatabaseSession newDatabaseSession) {
		super(newDatabaseSession);
	}

	@Override
	public Class<?> getExternalTransactionControllerClass() {
		return TxControlAdapter.class;
	}

	public static class TxControlAdapter extends AbstractTransactionController {

		/**
		 *  This has to be static because EclipseLink doesn't allow plugins
		 *  to be configured and passed in as instances. It is safe because
		 *  we use a separate ClassLoader every time we create the resource.
		 */
		private static TransactionControl txControl;
		
		public TxControlAdapter() {
			this.listenerFactory = new TxControlListenerFactory();
		}
		
		@Override
		public boolean isRolledBack_impl(Object status) {
			return status == ROLLED_BACK;
		}

		@Override
		protected void registerSynchronization_impl(AbstractSynchronizationListener listener, Object txn)
				throws Exception {
			TransactionContext ctx = ((TransactionWrapper) txn).getContext();
			ctx.preCompletion(listener::beforeCompletion);
			ctx.postCompletion(listener::afterCompletion);
		}

		@Override
		protected Object getTransaction_impl() throws Exception {
			TransactionContext currentContext = txControl.getCurrentContext();
			if(currentContext == null || currentContext.getTransactionStatus() == NO_TRANSACTION) {
				return null;
			} else {
				return new TransactionWrapper(currentContext);
			}
		}

		@Override
		protected Object getTransactionKey_impl(Object transaction) throws Exception {
			
			return transaction == null ? null :
				((TransactionWrapper) transaction).getContext().getTransactionKey();
		}

		@Override
		protected Object getTransactionStatus_impl() throws Exception {
			TransactionContext currentContext = txControl.getCurrentContext();
			return currentContext == null ? null : currentContext.getTransactionStatus();
		}

		@Override
		protected void beginTransaction_impl() throws Exception {
			throw new TransactionException("Open scoped transactions are not supported");
		}

		@Override
		protected void commitTransaction_impl() throws Exception {
			throw new TransactionException("Open scoped transactions are not supported");
		}

		@Override
		protected void rollbackTransaction_impl() throws Exception {
			throw new TransactionException("Open scoped transactions are not supported");
		}

		@Override
		protected void markTransactionForRollback_impl() throws Exception {
			txControl.setRollbackOnly();
		}

		@Override
		protected boolean canBeginTransaction_impl(Object status) {
			return false;
		}

		@Override
		protected boolean canCommitTransaction_impl(Object status) {
			return false;
		}

		@Override
		protected boolean canRollbackTransaction_impl(Object status) {
			return false;
		}

		@Override
		protected boolean canIssueSQLToDatabase_impl(Object status) {
			return status == ACTIVE || status == PREPARING;
		}

		@Override
		protected boolean canMergeUnitOfWork_impl(Object status) {
			return status == COMMITTED;
		}

		@Override
		protected String statusToString_impl(Object status) {
			return status == null ? "No scope is active" : status.toString();
		}
	}
	
	/** 
	 * We have to do this as despite its claims, EclipseLink JPA needs the
	 * transaction impl to be a javax.tranasaction.Transaction :(
	 */
	private static class TransactionWrapper implements Transaction {
		private final TransactionContext context;

		public TransactionWrapper(TransactionContext context) {
			this.context = context;
		}

		public TransactionContext getContext() {
			return context;
		}
		
		@Override
		public void registerSynchronization(Synchronization synch)
				throws IllegalStateException, RollbackException, SystemException {
			context.preCompletion(synch::beforeCompletion);
			context.postCompletion(status -> synch.afterCompletion(toIntStatus(status)));
		}

		@Override
		public void setRollbackOnly() throws IllegalStateException, SystemException {
			context.setRollbackOnly();
		}

		@Override
		public boolean delistResource(XAResource xaRes, int flag) throws IllegalStateException, SystemException {
			throw new TransactionException("Resources may not be delisted");
		}

		@Override
		public boolean enlistResource(XAResource xaRes)
				throws IllegalStateException, RollbackException, SystemException {
			context.registerXAResource(xaRes);
			return true;
		}

		@Override
		public int getStatus() throws SystemException {
			return toIntStatus(context.getTransactionStatus());
		}
		
		private int toIntStatus(TransactionStatus status) {
			switch(status) {
				case NO_TRANSACTION:
					return STATUS_NO_TRANSACTION;
				case ACTIVE:
					return STATUS_ACTIVE;
				case PREPARING:
					return STATUS_PREPARING;
				case PREPARED:
					return Status.STATUS_PREPARED;
				case COMMITTING:
					return STATUS_COMMITTING;
				case COMMITTED:
					return STATUS_COMMITTED;
				case MARKED_ROLLBACK:
					return STATUS_MARKED_ROLLBACK;
				case ROLLING_BACK:
					return STATUS_ROLLING_BACK;
				case ROLLED_BACK:
					return STATUS_ROLLEDBACK;
				default:
					return STATUS_UNKNOWN;
			}
		}

		@Override
		public void commit() throws HeuristicMixedException, HeuristicRollbackException, RollbackException,
				SecurityException, SystemException {
			throw new TransactionException("Open scoped transactions are not supported");
		}

		@Override
		public void rollback() throws IllegalStateException, SystemException {
			throw new TransactionException("Open scoped transactions are not supported");
		}
	}
	
	public static class TxControlListenerFactory implements SynchronizationListenerFactory {

		@Override
		public AbstractSynchronizationListener newSynchronizationListener(UnitOfWorkImpl unitOfWork,
				AbstractSession session, Object transaction, AbstractTransactionController controller) {
			return new TxControlListener(unitOfWork, session, transaction, controller);
		}
		
	}
	
	public static class TxControlListener extends AbstractSynchronizationListener {

		public TxControlListener(UnitOfWorkImpl unitOfWork, AbstractSession session, Object transaction,
				AbstractTransactionController controller) {
			super(unitOfWork, session, transaction, controller);
		}
	}
}
