package com.scienceminer.lookup.storage.lookup;

import com.codahale.metrics.Meter;
import com.scienceminer.lookup.reader.PmidReader;
import com.scienceminer.lookup.data.PmidData;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.lmdbjava.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.scienceminer.lookup.storage.BinarySerialiser;
import com.scienceminer.lookup.storage.StorageEnvFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.ByteBuffer.allocateDirect;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class PmidLookup {
    private static final Logger LOGGER = LoggerFactory.getLogger(PmidLookup.class);

    private final int DEFAULT_MAX_SIZE_LIST = 100;

    public static final String NAME_DOI2IDS = "pmidLookup_doi2ids";
    public static final String NAME_PMID2IDS = "pmid_lookup_pmid2ids";

    protected Env<ByteBuffer> environment;
    protected Dbi<ByteBuffer> dbDoiToIds;
    protected Dbi<ByteBuffer> dbPmidToIds;

    private final int batchSize;

    public PmidLookup(StorageEnvFactory storageEnvFactory) {
        this.environment = storageEnvFactory.getEnv();
        batchSize = storageEnvFactory.getConfiguration().getBatchSize();

        dbDoiToIds = this.environment.openDbi(NAME_DOI2IDS, DbiFlags.MDB_CREATE);
        dbPmidToIds = this.environment.openDbi(NAME_PMID2IDS, DbiFlags.MDB_CREATE);
    }

    public void loadFromFile(InputStream is, PmidReader reader, Meter metric) {
        final TransactionWrapper transactionWrapper = new TransactionWrapper(environment.txnWrite());
        final AtomicInteger counter = new AtomicInteger(0);

        reader.load(is, pmidData -> {
                    if (counter.get() == batchSize) {
                        transactionWrapper.tx.commit();
                        transactionWrapper.tx.close();
                        transactionWrapper.tx = environment.txnWrite();
                        counter.set(0);
                    }

                    if (isNotBlank(pmidData.getDoi())) {
                        store(dbDoiToIds, pmidData.getDoi(), pmidData, transactionWrapper.tx);
                    }

                    if (isNotBlank(pmidData.getPmid())) {
                        store(dbPmidToIds, pmidData.getPmid(), pmidData, transactionWrapper.tx);
                    }
                    metric.mark();
                    counter.incrementAndGet();
                }
        );
        transactionWrapper.tx.commit();
        transactionWrapper.tx.close();

        LOGGER.info("Cross checking number of records processed:: " + metric.getCount());
    }

    public PmidData retrieveIdsByDoi(String doi) {
        final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
        ByteBuffer cachedData = null;
        PmidData record = null;
        try (Txn<ByteBuffer> tx = environment.txnRead()) {
            keyBuffer.put(BinarySerialiser.serialize(doi)).flip();
            cachedData = dbDoiToIds.get(tx, keyBuffer);
            if (cachedData != null) {
                record = (PmidData) BinarySerialiser.deserialize(cachedData);
            }
        } catch (Exception e) {
            LOGGER.error("Cannot retrieve OA url having doi: " + doi, e);
        }

        return record;
    }

    public PmidData retrieveIdsByPmid(String pmid) {
        final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
        ByteBuffer cachedData = null;
        PmidData record = null;
        try (Txn<ByteBuffer> tx = environment.txnRead()) {
            keyBuffer.put(BinarySerialiser.serialize(pmid)).flip();
            cachedData = dbPmidToIds.get(tx, keyBuffer);
            if (cachedData != null) {
                record = (PmidData) BinarySerialiser.deserialize(cachedData);
            }
        } catch (Exception e) {
            LOGGER.error("Cannot retrieve OA url having doi: " + pmid, e);
        }

        return record;
    }

    public Map<String, Long> getSize() {
        Map<String, Long> size = new HashMap<>();
        try (final Txn<ByteBuffer> txn = this.environment.txnRead()) {
            size.put(NAME_DOI2IDS, dbDoiToIds.stat(txn).entries);
            size.put(NAME_PMID2IDS, dbPmidToIds.stat(txn).entries);
        }

        return size;
    }

    private void store(Dbi<ByteBuffer> db, String key, PmidData value, Txn<ByteBuffer> tx) {
        try {
            final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
            keyBuffer.put(BinarySerialiser.serialize(key)).flip();
            final byte[] serializedValue = BinarySerialiser.serialize(value);
            final ByteBuffer valBuffer = allocateDirect(serializedValue.length);
            valBuffer.put(serializedValue).flip();
            db.put(tx, keyBuffer, valBuffer);
        } catch (Exception e) {
            LOGGER.warn("Some serious issues when writing on LMDB database "
                    + db.toString() + " key: " + key + ", value: " + value, e);
        }
    }


    public List<Pair<String, PmidData>> retrieveList_pmidToIds(Integer total) {
        return retrieveList(total, dbPmidToIds);
    }

    public List<Pair<String, PmidData>> retrieveList_doiToIds(Integer total) {
        return retrieveList(total, dbDoiToIds);
    }

    public List<Pair<String, PmidData>> retrieveList(Integer total, Dbi<ByteBuffer> db) {
        if (total == null || total == 0) {
            total = DEFAULT_MAX_SIZE_LIST;
        }

        List<Pair<String, PmidData>> values = new ArrayList<>();
        int counter = 0;

        try (Txn<ByteBuffer> txn = environment.txnRead()) {
            try (CursorIterator<ByteBuffer> it = db.iterate(txn, KeyRange.all())) {
                for (final CursorIterator.KeyVal<ByteBuffer> kv : it.iterable()) {
                    values.add(new ImmutablePair<>((String) BinarySerialiser.deserialize(kv.key()), (PmidData) BinarySerialiser.deserialize(kv.val())));
                    if (total != null && counter == total) {
                        txn.close();
                        break;
                    }
                    counter++;
                }
            }
        }

        return values;
    }


    public boolean dropDb(String dbName) {
        if (StringUtils.equals(dbName, NAME_DOI2IDS)) {
            try (Txn<ByteBuffer> txn = environment.txnWrite()) {
                dbDoiToIds.drop(txn);
                txn.commit();
            }
            return true;
        } else if (StringUtils.equals(dbName, NAME_PMID2IDS)) {
            try (Txn<ByteBuffer> txn = environment.txnWrite()) {
                dbPmidToIds.drop(txn);
                txn.commit();
            }
            return true;
        }
        return false;
    }
}