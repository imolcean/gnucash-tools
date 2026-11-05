--
-- PostgreSQL database dump
--

\restrict 0yW0jt9L3kzXlyhlcapavBwLvyUKILhMGarovrfsPGggooCxC1BeOu6SL2PNFZt

-- Dumped from database version 17.11 (Debian 17.11-1.pgdg13+2)
-- Dumped by pg_dump version 17.11 (Debian 17.11-1.pgdg13+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: accounts; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.accounts (
    guid character varying(32) NOT NULL,
    name character varying(2048) NOT NULL,
    account_type character varying(2048) NOT NULL,
    commodity_guid character varying(32),
    commodity_scu integer NOT NULL,
    non_std_scu integer NOT NULL,
    parent_guid character varying(32),
    code character varying(2048),
    description character varying(2048),
    hidden integer,
    placeholder integer
);


ALTER TABLE public.accounts OWNER TO postgres;

--
-- Name: billterms; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.billterms (
    guid character varying(32) NOT NULL,
    name character varying(2048) NOT NULL,
    description character varying(2048) NOT NULL,
    refcount integer NOT NULL,
    invisible integer NOT NULL,
    parent character varying(32),
    type character varying(2048) NOT NULL,
    duedays integer,
    discountdays integer,
    discount_num bigint,
    discount_denom bigint,
    cutoff integer
);


ALTER TABLE public.billterms OWNER TO postgres;

--
-- Name: books; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.books (
    guid character varying(32) NOT NULL,
    root_account_guid character varying(32) NOT NULL,
    root_template_guid character varying(32) NOT NULL
);


ALTER TABLE public.books OWNER TO postgres;

--
-- Name: budget_amounts; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.budget_amounts (
    id integer NOT NULL,
    budget_guid character varying(32) NOT NULL,
    account_guid character varying(32) NOT NULL,
    period_num integer NOT NULL,
    amount_num bigint NOT NULL,
    amount_denom bigint NOT NULL
);


ALTER TABLE public.budget_amounts OWNER TO postgres;

--
-- Name: budget_amounts_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.budget_amounts_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.budget_amounts_id_seq OWNER TO postgres;

--
-- Name: budget_amounts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.budget_amounts_id_seq OWNED BY public.budget_amounts.id;


--
-- Name: budgets; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.budgets (
    guid character varying(32) NOT NULL,
    name character varying(2048) NOT NULL,
    description character varying(2048),
    num_periods integer NOT NULL
);


ALTER TABLE public.budgets OWNER TO postgres;

--
-- Name: commodities; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.commodities (
    guid character varying(32) NOT NULL,
    namespace character varying(2048) NOT NULL,
    mnemonic character varying(2048) NOT NULL,
    fullname character varying(2048),
    cusip character varying(2048),
    fraction integer NOT NULL,
    quote_flag integer NOT NULL,
    quote_source character varying(2048),
    quote_tz character varying(2048)
);


ALTER TABLE public.commodities OWNER TO postgres;

--
-- Name: customers; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.customers (
    guid character varying(32) NOT NULL,
    name character varying(2048) NOT NULL,
    id character varying(2048) NOT NULL,
    notes character varying(2048) NOT NULL,
    active integer NOT NULL,
    discount_num bigint NOT NULL,
    discount_denom bigint NOT NULL,
    credit_num bigint NOT NULL,
    credit_denom bigint NOT NULL,
    currency character varying(32) NOT NULL,
    tax_override integer NOT NULL,
    addr_name character varying(1024),
    addr_addr1 character varying(1024),
    addr_addr2 character varying(1024),
    addr_addr3 character varying(1024),
    addr_addr4 character varying(1024),
    addr_phone character varying(128),
    addr_fax character varying(128),
    addr_email character varying(256),
    shipaddr_name character varying(1024),
    shipaddr_addr1 character varying(1024),
    shipaddr_addr2 character varying(1024),
    shipaddr_addr3 character varying(1024),
    shipaddr_addr4 character varying(1024),
    shipaddr_phone character varying(128),
    shipaddr_fax character varying(128),
    shipaddr_email character varying(256),
    terms character varying(32),
    tax_included integer,
    taxtable character varying(32)
);


ALTER TABLE public.customers OWNER TO postgres;

--
-- Name: employees; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.employees (
    guid character varying(32) NOT NULL,
    username character varying(2048) NOT NULL,
    id character varying(2048) NOT NULL,
    language character varying(2048) NOT NULL,
    acl character varying(2048) NOT NULL,
    active integer NOT NULL,
    currency character varying(32) NOT NULL,
    ccard_guid character varying(32),
    workday_num bigint NOT NULL,
    workday_denom bigint NOT NULL,
    rate_num bigint NOT NULL,
    rate_denom bigint NOT NULL,
    addr_name character varying(1024),
    addr_addr1 character varying(1024),
    addr_addr2 character varying(1024),
    addr_addr3 character varying(1024),
    addr_addr4 character varying(1024),
    addr_phone character varying(128),
    addr_fax character varying(128),
    addr_email character varying(256)
);


ALTER TABLE public.employees OWNER TO postgres;

--
-- Name: entries; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.entries (
    guid character varying(32) NOT NULL,
    date timestamp without time zone NOT NULL,
    date_entered timestamp without time zone,
    description character varying(2048),
    action character varying(2048),
    notes character varying(2048),
    quantity_num bigint,
    quantity_denom bigint,
    i_acct character varying(32),
    i_price_num bigint,
    i_price_denom bigint,
    i_discount_num bigint,
    i_discount_denom bigint,
    invoice character varying(32),
    i_disc_type character varying(2048),
    i_disc_how character varying(2048),
    i_taxable integer,
    i_taxincluded integer,
    i_taxtable character varying(32),
    b_acct character varying(32),
    b_price_num bigint,
    b_price_denom bigint,
    bill character varying(32),
    b_taxable integer,
    b_taxincluded integer,
    b_taxtable character varying(32),
    b_paytype integer,
    billable integer,
    billto_type integer,
    billto_guid character varying(32),
    order_guid character varying(32)
);


ALTER TABLE public.entries OWNER TO postgres;

--
-- Name: gnclock; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.gnclock (
    hostname character varying(255),
    pid integer
);


ALTER TABLE public.gnclock OWNER TO postgres;

--
-- Name: invoices; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.invoices (
    guid character varying(32) NOT NULL,
    id character varying(2048) NOT NULL,
    date_opened timestamp without time zone,
    date_posted timestamp without time zone,
    notes character varying(2048) NOT NULL,
    active integer NOT NULL,
    currency character varying(32) NOT NULL,
    owner_type integer,
    owner_guid character varying(32),
    terms character varying(32),
    billing_id character varying(2048),
    post_txn character varying(32),
    post_lot character varying(32),
    post_acc character varying(32),
    billto_type integer,
    billto_guid character varying(32),
    charge_amt_num bigint,
    charge_amt_denom bigint
);


ALTER TABLE public.invoices OWNER TO postgres;

--
-- Name: jobs; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.jobs (
    guid character varying(32) NOT NULL,
    id character varying(2048) NOT NULL,
    name character varying(2048) NOT NULL,
    reference character varying(2048) NOT NULL,
    active integer NOT NULL,
    owner_type integer,
    owner_guid character varying(32)
);


ALTER TABLE public.jobs OWNER TO postgres;

--
-- Name: lots; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.lots (
    guid character varying(32) NOT NULL,
    account_guid character varying(32),
    is_closed integer NOT NULL
);


ALTER TABLE public.lots OWNER TO postgres;

--
-- Name: orders; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.orders (
    guid character varying(32) NOT NULL,
    id character varying(2048) NOT NULL,
    notes character varying(2048) NOT NULL,
    reference character varying(2048) NOT NULL,
    active integer NOT NULL,
    date_opened timestamp without time zone NOT NULL,
    date_closed timestamp without time zone NOT NULL,
    owner_type integer NOT NULL,
    owner_guid character varying(32) NOT NULL
);


ALTER TABLE public.orders OWNER TO postgres;

--
-- Name: prices; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.prices (
    guid character varying(32) NOT NULL,
    commodity_guid character varying(32) NOT NULL,
    currency_guid character varying(32) NOT NULL,
    date timestamp without time zone NOT NULL,
    source character varying(2048),
    type character varying(2048),
    value_num bigint NOT NULL,
    value_denom bigint NOT NULL
);


ALTER TABLE public.prices OWNER TO postgres;

--
-- Name: recurrences; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.recurrences (
    id integer NOT NULL,
    obj_guid character varying(32) NOT NULL,
    recurrence_mult integer NOT NULL,
    recurrence_period_type character varying(2048) NOT NULL,
    recurrence_period_start date NOT NULL,
    recurrence_weekend_adjust character varying(2048) NOT NULL
);


ALTER TABLE public.recurrences OWNER TO postgres;

--
-- Name: recurrences_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.recurrences_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.recurrences_id_seq OWNER TO postgres;

--
-- Name: recurrences_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.recurrences_id_seq OWNED BY public.recurrences.id;


--
-- Name: schedxactions; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.schedxactions (
    guid character varying(32) NOT NULL,
    name character varying(2048),
    enabled integer NOT NULL,
    start_date date,
    end_date date,
    last_occur date,
    num_occur integer NOT NULL,
    rem_occur integer NOT NULL,
    auto_create integer NOT NULL,
    auto_notify integer NOT NULL,
    adv_creation integer NOT NULL,
    adv_notify integer NOT NULL,
    instance_count integer NOT NULL,
    template_act_guid character varying(32) NOT NULL
);


ALTER TABLE public.schedxactions OWNER TO postgres;

--
-- Name: slots; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.slots (
    id integer NOT NULL,
    obj_guid character varying(32) NOT NULL,
    name character varying(4096) NOT NULL,
    slot_type integer NOT NULL,
    int64_val bigint,
    string_val character varying(4096),
    double_val double precision,
    timespec_val timestamp without time zone,
    guid_val character varying(32),
    numeric_val_num bigint,
    numeric_val_denom bigint,
    gdate_val date
);


ALTER TABLE public.slots OWNER TO postgres;

--
-- Name: slots_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.slots_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.slots_id_seq OWNER TO postgres;

--
-- Name: slots_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.slots_id_seq OWNED BY public.slots.id;


--
-- Name: splits; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.splits (
    guid character varying(32) NOT NULL,
    tx_guid character varying(32) NOT NULL,
    account_guid character varying(32) NOT NULL,
    memo character varying(2048) NOT NULL,
    action character varying(2048) NOT NULL,
    reconcile_state character varying(1) NOT NULL,
    reconcile_date timestamp without time zone,
    value_num bigint NOT NULL,
    value_denom bigint NOT NULL,
    quantity_num bigint NOT NULL,
    quantity_denom bigint NOT NULL,
    lot_guid character varying(32)
);


ALTER TABLE public.splits OWNER TO postgres;

--
-- Name: taxtable_entries; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.taxtable_entries (
    id integer NOT NULL,
    taxtable character varying(32) NOT NULL,
    account character varying(32) NOT NULL,
    amount_num bigint NOT NULL,
    amount_denom bigint NOT NULL,
    type integer NOT NULL
);


ALTER TABLE public.taxtable_entries OWNER TO postgres;

--
-- Name: taxtable_entries_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.taxtable_entries_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.taxtable_entries_id_seq OWNER TO postgres;

--
-- Name: taxtable_entries_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.taxtable_entries_id_seq OWNED BY public.taxtable_entries.id;


--
-- Name: taxtables; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.taxtables (
    guid character varying(32) NOT NULL,
    name character varying(50) NOT NULL,
    refcount bigint NOT NULL,
    invisible integer NOT NULL,
    parent character varying(32)
);


ALTER TABLE public.taxtables OWNER TO postgres;

--
-- Name: transactions; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.transactions (
    guid character varying(32) NOT NULL,
    currency_guid character varying(32) NOT NULL,
    num character varying(2048) NOT NULL,
    post_date timestamp without time zone,
    enter_date timestamp without time zone,
    description character varying(2048)
);


ALTER TABLE public.transactions OWNER TO postgres;

--
-- Name: vendors; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.vendors (
    guid character varying(32) NOT NULL,
    name character varying(2048) NOT NULL,
    id character varying(2048) NOT NULL,
    notes character varying(2048) NOT NULL,
    currency character varying(32) NOT NULL,
    active integer NOT NULL,
    tax_override integer NOT NULL,
    addr_name character varying(1024),
    addr_addr1 character varying(1024),
    addr_addr2 character varying(1024),
    addr_addr3 character varying(1024),
    addr_addr4 character varying(1024),
    addr_phone character varying(128),
    addr_fax character varying(128),
    addr_email character varying(256),
    terms character varying(32),
    tax_inc character varying(2048),
    tax_table character varying(32)
);


ALTER TABLE public.vendors OWNER TO postgres;

--
-- Name: versions; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.versions (
    table_name character varying(50) NOT NULL,
    table_version integer NOT NULL
);


ALTER TABLE public.versions OWNER TO postgres;

--
-- Name: budget_amounts id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.budget_amounts ALTER COLUMN id SET DEFAULT nextval('public.budget_amounts_id_seq'::regclass);


--
-- Name: recurrences id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.recurrences ALTER COLUMN id SET DEFAULT nextval('public.recurrences_id_seq'::regclass);


--
-- Name: slots id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.slots ALTER COLUMN id SET DEFAULT nextval('public.slots_id_seq'::regclass);


--
-- Name: taxtable_entries id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.taxtable_entries ALTER COLUMN id SET DEFAULT nextval('public.taxtable_entries_id_seq'::regclass);


--
-- Data for Name: accounts; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.accounts (guid, name, account_type, commodity_guid, commodity_scu, non_std_scu, parent_guid, code, description, hidden, placeholder) FROM stdin;
738965f089a042abb6859881db910878	Root Account	ROOT	7c8bcccddbb24b44a3418ca6dbf87106	100	0	\N			0	0
1a2898f703dc46a9a6bd160bd20f4ce3	Assets	ASSET	7c8bcccddbb24b44a3418ca6dbf87106	100	0	738965f089a042abb6859881db910878		Assets	0	1
c484ef2fae214937865e1dbecc3bba0f	Cash	CASH	7c8bcccddbb24b44a3418ca6dbf87106	100	0	1a2898f703dc46a9a6bd160bd20f4ce3		Cash in Wallet	0	0
f98d0fcc0285458cb20c11240b47f75a	Checking	BANK	7c8bcccddbb24b44a3418ca6dbf87106	100	0	1a2898f703dc46a9a6bd160bd20f4ce3		Checking Account	0	0
90678c06f60b44caa83ac70c2b49a0c0	Cash USD	ASSET	1943723210fa4df184b6237b15fabe9a	100	0	1a2898f703dc46a9a6bd160bd20f4ce3			0	0
6c802f1e97234ec69174a8eebf8a2b20	Liabilities	LIABILITY	7c8bcccddbb24b44a3418ca6dbf87106	100	0	738965f089a042abb6859881db910878		Liabilities	0	1
bfb633fa8b564e309429cf082efa5e06	Credit Card	CREDIT	7c8bcccddbb24b44a3418ca6dbf87106	100	0	6c802f1e97234ec69174a8eebf8a2b20		Credit Card	0	0
d26dc74527ee4236af2851a31f1e5d95	Income	INCOME	7c8bcccddbb24b44a3418ca6dbf87106	100	0	738965f089a042abb6859881db910878		Income	0	1
3e761d35833d4eca85f77ff8d8757fc3	Salary	INCOME	7c8bcccddbb24b44a3418ca6dbf87106	100	0	d26dc74527ee4236af2851a31f1e5d95		Salary	0	0
23e4811038d34013ba9be87db17ec3b5	Expenses	EXPENSE	7c8bcccddbb24b44a3418ca6dbf87106	100	0	738965f089a042abb6859881db910878		Expenses	0	1
9297fd595c234e5981140cb09ef4c7ac	Food	EXPENSE	7c8bcccddbb24b44a3418ca6dbf87106	100	0	23e4811038d34013ba9be87db17ec3b5		Food	0	1
0e0675c2c42b4c19bffa999c4932f3a8	Groceries	EXPENSE	7c8bcccddbb24b44a3418ca6dbf87106	100	0	9297fd595c234e5981140cb09ef4c7ac		Supermarkets	0	0
2122c37fb8ef4129978b6a8aa8cacc41	Rent	EXPENSE	7c8bcccddbb24b44a3418ca6dbf87106	100	0	23e4811038d34013ba9be87db17ec3b5			0	0
2be53f0031e0408cafd491c44cdefac5	Misc	EXPENSE	7c8bcccddbb24b44a3418ca6dbf87106	100	0	23e4811038d34013ba9be87db17ec3b5			0	0
1046d9e3b0d74643b5dd3f31e26cb36a	Equity	EQUITY	7c8bcccddbb24b44a3418ca6dbf87106	100	0	738965f089a042abb6859881db910878		Equity	0	1
bf6d4c349d4a4e51813079c9d16ee76d	Opening Balances	EQUITY	7c8bcccddbb24b44a3418ca6dbf87106	100	0	1046d9e3b0d74643b5dd3f31e26cb36a		Opening Balances	0	0
588435c1276b403eb27a505a681ed33d	Hidden	ASSET	7c8bcccddbb24b44a3418ca6dbf87106	100	0	738965f089a042abb6859881db910878			1	1
92890de0b32c40f5a67afe144d6087f3	Hidden nested	ASSET	7c8bcccddbb24b44a3418ca6dbf87106	100	0	588435c1276b403eb27a505a681ed33d			1	0
0f0bd18f3f38458d8059b3099d68fb9f	Visible nested	ASSET	7c8bcccddbb24b44a3418ca6dbf87106	100	0	588435c1276b403eb27a505a681ed33d			0	0
bf28af07cd8a4735890d7370ea09b917	Trading	TRADING	7c8bcccddbb24b44a3418ca6dbf87106	100	0	738965f089a042abb6859881db910878			0	1
4dea01cf6e3d4418b3d8ebac98d111ed	CURRENCY	TRADING	7c8bcccddbb24b44a3418ca6dbf87106	100	0	bf28af07cd8a4735890d7370ea09b917			0	1
b6cee3641d1740e3925ffeb28ac37637	USD	TRADING	1943723210fa4df184b6237b15fabe9a	100	0	4dea01cf6e3d4418b3d8ebac98d111ed			0	0
25d4d674e04b49a798ab2962e86450b5	EUR	TRADING	7c8bcccddbb24b44a3418ca6dbf87106	100	0	4dea01cf6e3d4418b3d8ebac98d111ed			0	0
3168b7fe01be4a8ab6cc369b7ff146b9	Template Root	ROOT	\N	0	0	\N			0	0
\.


--
-- Data for Name: billterms; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.billterms (guid, name, description, refcount, invisible, parent, type, duedays, discountdays, discount_num, discount_denom, cutoff) FROM stdin;
\.


--
-- Data for Name: books; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.books (guid, root_account_guid, root_template_guid) FROM stdin;
319ebab3b09e4fbba61d632426feb462	738965f089a042abb6859881db910878	3168b7fe01be4a8ab6cc369b7ff146b9
\.


--
-- Data for Name: budget_amounts; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.budget_amounts (id, budget_guid, account_guid, period_num, amount_num, amount_denom) FROM stdin;
\.


--
-- Data for Name: budgets; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.budgets (guid, name, description, num_periods) FROM stdin;
\.


--
-- Data for Name: commodities; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.commodities (guid, namespace, mnemonic, fullname, cusip, fraction, quote_flag, quote_source, quote_tz) FROM stdin;
7c8bcccddbb24b44a3418ca6dbf87106	CURRENCY	EUR	Euro	978	100	1	currency	
1943723210fa4df184b6237b15fabe9a	CURRENCY	USD	US Dollar	840	100	1	currency	
\.


--
-- Data for Name: customers; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.customers (guid, name, id, notes, active, discount_num, discount_denom, credit_num, credit_denom, currency, tax_override, addr_name, addr_addr1, addr_addr2, addr_addr3, addr_addr4, addr_phone, addr_fax, addr_email, shipaddr_name, shipaddr_addr1, shipaddr_addr2, shipaddr_addr3, shipaddr_addr4, shipaddr_phone, shipaddr_fax, shipaddr_email, terms, tax_included, taxtable) FROM stdin;
\.


--
-- Data for Name: employees; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.employees (guid, username, id, language, acl, active, currency, ccard_guid, workday_num, workday_denom, rate_num, rate_denom, addr_name, addr_addr1, addr_addr2, addr_addr3, addr_addr4, addr_phone, addr_fax, addr_email) FROM stdin;
\.


--
-- Data for Name: entries; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.entries (guid, date, date_entered, description, action, notes, quantity_num, quantity_denom, i_acct, i_price_num, i_price_denom, i_discount_num, i_discount_denom, invoice, i_disc_type, i_disc_how, i_taxable, i_taxincluded, i_taxtable, b_acct, b_price_num, b_price_denom, bill, b_taxable, b_taxincluded, b_taxtable, b_paytype, billable, billto_type, billto_guid, order_guid) FROM stdin;
\.


--
-- Data for Name: gnclock; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.gnclock (hostname, pid) FROM stdin;
Leopard	8300
\.


--
-- Data for Name: invoices; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.invoices (guid, id, date_opened, date_posted, notes, active, currency, owner_type, owner_guid, terms, billing_id, post_txn, post_lot, post_acc, billto_type, billto_guid, charge_amt_num, charge_amt_denom) FROM stdin;
\.


--
-- Data for Name: jobs; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.jobs (guid, id, name, reference, active, owner_type, owner_guid) FROM stdin;
\.


--
-- Data for Name: lots; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.lots (guid, account_guid, is_closed) FROM stdin;
\.


--
-- Data for Name: orders; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.orders (guid, id, notes, reference, active, date_opened, date_closed, owner_type, owner_guid) FROM stdin;
\.


--
-- Data for Name: prices; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.prices (guid, commodity_guid, currency_guid, date, source, type, value_num, value_denom) FROM stdin;
7f719c1881294f9ba8f5fe9b7f594f17	1943723210fa4df184b6237b15fabe9a	7c8bcccddbb24b44a3418ca6dbf87106	2026-11-04 10:59:00	user:xfer-dialog	transaction	13	10
\.


--
-- Data for Name: recurrences; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.recurrences (id, obj_guid, recurrence_mult, recurrence_period_type, recurrence_period_start, recurrence_weekend_adjust) FROM stdin;
\.


--
-- Data for Name: schedxactions; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.schedxactions (guid, name, enabled, start_date, end_date, last_occur, num_occur, rem_occur, auto_create, auto_notify, adv_creation, adv_notify, instance_count, template_act_guid) FROM stdin;
\.


--
-- Data for Name: slots; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.slots (id, obj_guid, name, slot_type, int64_val, string_val, double_val, timespec_val, guid_val, numeric_val_num, numeric_val_denom, gdate_val) FROM stdin;
1	319ebab3b09e4fbba61d632426feb462	counter_formats	9	0	\N	\N	1970-01-01 00:00:00	8dad8fd68d024154b3ca079c164c3628	0	1	\N
2	8dad8fd68d024154b3ca079c164c3628	counter_formats/gncBill	4	0		\N	1970-01-01 00:00:00	\N	0	1	\N
3	8dad8fd68d024154b3ca079c164c3628	counter_formats/gncCustomer	4	0		\N	1970-01-01 00:00:00	\N	0	1	\N
4	8dad8fd68d024154b3ca079c164c3628	counter_formats/gncEmployee	4	0		\N	1970-01-01 00:00:00	\N	0	1	\N
5	8dad8fd68d024154b3ca079c164c3628	counter_formats/gncExpVoucher	4	0		\N	1970-01-01 00:00:00	\N	0	1	\N
6	8dad8fd68d024154b3ca079c164c3628	counter_formats/gncInvoice	4	0		\N	1970-01-01 00:00:00	\N	0	1	\N
7	8dad8fd68d024154b3ca079c164c3628	counter_formats/gncJob	4	0		\N	1970-01-01 00:00:00	\N	0	1	\N
8	8dad8fd68d024154b3ca079c164c3628	counter_formats/gncOrder	4	0		\N	1970-01-01 00:00:00	\N	0	1	\N
9	8dad8fd68d024154b3ca079c164c3628	counter_formats/gncVendor	4	0		\N	1970-01-01 00:00:00	\N	0	1	\N
10	319ebab3b09e4fbba61d632426feb462	counters	9	0	\N	\N	1970-01-01 00:00:00	6f01bba46cf14b3d9d4b8b181569b24e	0	1	\N
11	6f01bba46cf14b3d9d4b8b181569b24e	counters/gncBill	1	0	\N	\N	1970-01-01 00:00:00	\N	0	1	\N
12	6f01bba46cf14b3d9d4b8b181569b24e	counters/gncCustomer	1	0	\N	\N	1970-01-01 00:00:00	\N	0	1	\N
13	6f01bba46cf14b3d9d4b8b181569b24e	counters/gncEmployee	1	0	\N	\N	1970-01-01 00:00:00	\N	0	1	\N
14	6f01bba46cf14b3d9d4b8b181569b24e	counters/gncExpVoucher	1	0	\N	\N	1970-01-01 00:00:00	\N	0	1	\N
15	6f01bba46cf14b3d9d4b8b181569b24e	counters/gncInvoice	1	0	\N	\N	1970-01-01 00:00:00	\N	0	1	\N
16	6f01bba46cf14b3d9d4b8b181569b24e	counters/gncJob	1	0	\N	\N	1970-01-01 00:00:00	\N	0	1	\N
17	6f01bba46cf14b3d9d4b8b181569b24e	counters/gncOrder	1	0	\N	\N	1970-01-01 00:00:00	\N	0	1	\N
18	6f01bba46cf14b3d9d4b8b181569b24e	counters/gncVendor	1	0	\N	\N	1970-01-01 00:00:00	\N	0	1	\N
19	319ebab3b09e4fbba61d632426feb462	features	9	0	\N	\N	1970-01-01 00:00:00	a6d7b5f7bea745d8a190969adf512acf	0	1	\N
20	a6d7b5f7bea745d8a190969adf512acf	features/ISO-8601 formatted date strings in SQLite3 databases.	4	0	Use ISO formatted date-time strings in SQLite3 databases (requires at least GnuCash 2.6.20)	\N	1970-01-01 00:00:00	\N	0	1	\N
21	a6d7b5f7bea745d8a190969adf512acf	features/Register sort and filter settings stored in .gcm file	4	0	Store the register sort and filter settings in .gcm metadata file (requires at least GnuCash 3.3)	\N	1970-01-01 00:00:00	\N	0	1	\N
22	a6d7b5f7bea745d8a190969adf512acf	features/Use a dedicated opening balance account identified by an 'equity-type' slot	4	0	Use a dedicated opening balance account identified by an 'equity-type' slot (requires at least Gnucash 4.3)	\N	1970-01-01 00:00:00	\N	0	1	\N
23	319ebab3b09e4fbba61d632426feb462	options	9	0	\N	\N	1970-01-01 00:00:00	4abffa1c8ce64915a6e0e13e070584c6	0	1	\N
24	4abffa1c8ce64915a6e0e13e070584c6	options/Accounts	9	0	\N	\N	1970-01-01 00:00:00	0d98a74b5291423c865f4a47356d6592	0	1	\N
25	0d98a74b5291423c865f4a47356d6592	options/Accounts/Day Threshold for Read-Only Transactions (red line)	2	0	\N	0	1970-01-01 00:00:00	\N	0	1	\N
26	0d98a74b5291423c865f4a47356d6592	options/Accounts/Use Split Action Field for Number	4	0	f	\N	1970-01-01 00:00:00	\N	0	1	\N
27	0d98a74b5291423c865f4a47356d6592	options/Accounts/Use Trading Accounts	4	0	t	\N	1970-01-01 00:00:00	\N	0	1	\N
28	4abffa1c8ce64915a6e0e13e070584c6	options/Business	9	0	\N	\N	1970-01-01 00:00:00	a14b4b58d42c42608c0eddf7181a10f4	0	1	\N
29	a14b4b58d42c42608c0eddf7181a10f4	options/Business/Company Address	4	0		\N	1970-01-01 00:00:00	\N	0	1	\N
30	a14b4b58d42c42608c0eddf7181a10f4	options/Business/Company Contact Person	4	0		\N	1970-01-01 00:00:00	\N	0	1	\N
31	a14b4b58d42c42608c0eddf7181a10f4	options/Business/Company Email Address	4	0		\N	1970-01-01 00:00:00	\N	0	1	\N
32	a14b4b58d42c42608c0eddf7181a10f4	options/Business/Company Fax Number	4	0		\N	1970-01-01 00:00:00	\N	0	1	\N
33	a14b4b58d42c42608c0eddf7181a10f4	options/Business/Company ID	4	0		\N	1970-01-01 00:00:00	\N	0	1	\N
34	a14b4b58d42c42608c0eddf7181a10f4	options/Business/Company Name	4	0		\N	1970-01-01 00:00:00	\N	0	1	\N
35	a14b4b58d42c42608c0eddf7181a10f4	options/Business/Company Phone Number	4	0		\N	1970-01-01 00:00:00	\N	0	1	\N
36	a14b4b58d42c42608c0eddf7181a10f4	options/Business/Company Website URL	4	0		\N	1970-01-01 00:00:00	\N	0	1	\N
37	a14b4b58d42c42608c0eddf7181a10f4	options/Business/Default Invoice Report	4	0	5123a759ceb9483abf2182d01c140e8d/Printable Invoice	\N	1970-01-01 00:00:00	\N	0	1	\N
38	a14b4b58d42c42608c0eddf7181a10f4	options/Business/Default Invoice Report Timeout	2	0	\N	0	1970-01-01 00:00:00	\N	0	1	\N
39	4abffa1c8ce64915a6e0e13e070584c6	options/Tax	9	0	\N	\N	1970-01-01 00:00:00	1eef05ea76e84396aafb021ee8125d0b	0	1	\N
40	1eef05ea76e84396aafb021ee8125d0b	options/Tax/Tax Number	4	0		\N	1970-01-01 00:00:00	\N	0	1	\N
41	319ebab3b09e4fbba61d632426feb462	remove-color-not-set-slots	4	0	true	\N	1970-01-01 00:00:00	\N	0	1	\N
42	1a2898f703dc46a9a6bd160bd20f4ce3	placeholder	4	0	true	\N	1970-01-01 00:00:00	\N	0	1	\N
43	c484ef2fae214937865e1dbecc3bba0f	balance-limit	9	0	\N	\N	1970-01-01 00:00:00	e606391de077441cbbfa2284f5b41e17	0	1	\N
44	c484ef2fae214937865e1dbecc3bba0f	reconcile-info	9	0	\N	\N	1970-01-01 00:00:00	b583cd351ba4431c83aaadeafca7a95e	0	1	\N
45	b583cd351ba4431c83aaadeafca7a95e	reconcile-info/include-children	1	0	\N	\N	1970-01-01 00:00:00	\N	0	1	\N
46	b583cd351ba4431c83aaadeafca7a95e	reconcile-info/last-date	1	1793660399	\N	\N	1970-01-01 00:00:00	\N	0	1	\N
47	f98d0fcc0285458cb20c11240b47f75a	balance-limit	9	0	\N	\N	1970-01-01 00:00:00	8f462fe616bc4070bac90db55b81bfa7	0	1	\N
48	90678c06f60b44caa83ac70c2b49a0c0	balance-limit	9	0	\N	\N	1970-01-01 00:00:00	6ad4f02628ed4e4a8841a851076af107	0	1	\N
49	6c802f1e97234ec69174a8eebf8a2b20	placeholder	4	0	true	\N	1970-01-01 00:00:00	\N	0	1	\N
50	d26dc74527ee4236af2851a31f1e5d95	placeholder	4	0	true	\N	1970-01-01 00:00:00	\N	0	1	\N
51	23e4811038d34013ba9be87db17ec3b5	placeholder	4	0	true	\N	1970-01-01 00:00:00	\N	0	1	\N
52	9297fd595c234e5981140cb09ef4c7ac	balance-limit	9	0	\N	\N	1970-01-01 00:00:00	b4fb8498cdcd4b0982a971b068cc0058	0	1	\N
53	9297fd595c234e5981140cb09ef4c7ac	placeholder	4	0	true	\N	1970-01-01 00:00:00	\N	0	1	\N
54	0e0675c2c42b4c19bffa999c4932f3a8	balance-limit	9	0	\N	\N	1970-01-01 00:00:00	3a027e33b34b4217b89fd664cfd8bd31	0	1	\N
55	2122c37fb8ef4129978b6a8aa8cacc41	balance-limit	9	0	\N	\N	1970-01-01 00:00:00	5278be1fceff4523a22918971c039ef5	0	1	\N
56	2be53f0031e0408cafd491c44cdefac5	balance-limit	9	0	\N	\N	1970-01-01 00:00:00	03c51a2dc64c491d9a7750e1244001c3	0	1	\N
57	1046d9e3b0d74643b5dd3f31e26cb36a	placeholder	4	0	true	\N	1970-01-01 00:00:00	\N	0	1	\N
58	bf6d4c349d4a4e51813079c9d16ee76d	equity-type	4	0	opening-balance	\N	1970-01-01 00:00:00	\N	0	1	\N
59	588435c1276b403eb27a505a681ed33d	balance-limit	9	0	\N	\N	1970-01-01 00:00:00	d9ab33819321449a8afe5c4157b7f1dd	0	1	\N
60	588435c1276b403eb27a505a681ed33d	hidden	4	0	true	\N	1970-01-01 00:00:00	\N	0	1	\N
61	588435c1276b403eb27a505a681ed33d	placeholder	4	0	true	\N	1970-01-01 00:00:00	\N	0	1	\N
62	92890de0b32c40f5a67afe144d6087f3	balance-limit	9	0	\N	\N	1970-01-01 00:00:00	22a384dde8ad48be8c6b474cb57956b1	0	1	\N
63	92890de0b32c40f5a67afe144d6087f3	hidden	4	0	true	\N	1970-01-01 00:00:00	\N	0	1	\N
64	0f0bd18f3f38458d8059b3099d68fb9f	balance-limit	9	0	\N	\N	1970-01-01 00:00:00	7de3d1853783446a95dcea17b5abd3aa	0	1	\N
65	bf28af07cd8a4735890d7370ea09b917	placeholder	4	0	true	\N	1970-01-01 00:00:00	\N	0	1	\N
66	4dea01cf6e3d4418b3d8ebac98d111ed	placeholder	4	0	true	\N	1970-01-01 00:00:00	\N	0	1	\N
67	4a59d6b5fdfe499f946b45cc70df5d07	date-posted	10	0	\N	\N	1970-01-01 00:00:00	\N	0	1	2026-11-01
68	bd4c936ae9124d76a2fdcebf171fec15	date-posted	10	0	\N	\N	1970-01-01 00:00:00	\N	0	1	2026-11-02
69	744c38b61f404779bef319baf12f8d6a	date-posted	10	0	\N	\N	1970-01-01 00:00:00	\N	0	1	2026-11-02
70	744c38b61f404779bef319baf12f8d6a	notes	4	0	Forgot something	\N	1970-01-01 00:00:00	\N	0	1	\N
71	bb5db0bc2b7b4dcfb357ecebafc81b0a	date-posted	10	0	\N	\N	1970-01-01 00:00:00	\N	0	1	2026-11-03
72	eb0e959fe75d4084a5df98d17f7ffdcf	date-posted	10	0	\N	\N	1970-01-01 00:00:00	\N	0	1	2026-11-03
73	42a6cf57cd004b62a66f1190bd34eb75	date-posted	10	0	\N	\N	1970-01-01 00:00:00	\N	0	1	2026-11-03
74	c8bf40fdb201416a96af8b22b94f7a2f	date-posted	10	0	\N	\N	1970-01-01 00:00:00	\N	0	1	2026-11-04
75	238d64e1d17c4be08cff427b8c1139a2	date-posted	10	0	\N	\N	1970-01-01 00:00:00	\N	0	1	2026-11-10
\.


--
-- Data for Name: splits; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.splits (guid, tx_guid, account_guid, memo, action, reconcile_state, reconcile_date, value_num, value_denom, quantity_num, quantity_denom, lot_guid) FROM stdin;
6b8de1fd75c64ddfa41f3ef60507dd88	4a59d6b5fdfe499f946b45cc70df5d07	c484ef2fae214937865e1dbecc3bba0f			y	2026-11-02 22:59:59	10000	100	10000	100	\N
7951bceae88243359f3930b2db6dddf4	4a59d6b5fdfe499f946b45cc70df5d07	bf6d4c349d4a4e51813079c9d16ee76d			n	1970-01-01 00:00:00	-10000	100	-10000	100	\N
082c49f7e9f04269a95a9dca1ddcb0c2	bd4c936ae9124d76a2fdcebf171fec15	0e0675c2c42b4c19bffa999c4932f3a8			n	1970-01-01 00:00:00	1234	100	1234	100	\N
ab4d042036f245c7a6e7c9d0bc838801	bd4c936ae9124d76a2fdcebf171fec15	c484ef2fae214937865e1dbecc3bba0f			y	2026-11-02 22:59:59	-1234	100	-1234	100	\N
9ef76a7f8b1b4ac6baf1076be3ab7997	744c38b61f404779bef319baf12f8d6a	0e0675c2c42b4c19bffa999c4932f3a8			n	1970-01-01 00:00:00	766	100	766	100	\N
2c0dd840499e45b68d8c4939fa4ce6d9	744c38b61f404779bef319baf12f8d6a	c484ef2fae214937865e1dbecc3bba0f			y	2026-11-02 22:59:59	-766	100	-766	100	\N
5c1480625dec4a78b1c5e87e75b7507e	bb5db0bc2b7b4dcfb357ecebafc81b0a	c484ef2fae214937865e1dbecc3bba0f			n	1970-01-01 00:00:00	150000	100	150000	100	\N
6eb671844a0c42b88714259368ec0286	bb5db0bc2b7b4dcfb357ecebafc81b0a	2122c37fb8ef4129978b6a8aa8cacc41	Company-paid flat		n	1970-01-01 00:00:00	50000	100	50000	100	\N
e952d32afd72418980cfe9e54430348e	bb5db0bc2b7b4dcfb357ecebafc81b0a	3e761d35833d4eca85f77ff8d8757fc3			n	1970-01-01 00:00:00	-200000	100	-200000	100	\N
a78b2f619ec442c7ada33ad31d104f4b	eb0e959fe75d4084a5df98d17f7ffdcf	2122c37fb8ef4129978b6a8aa8cacc41			n	1970-01-01 00:00:00	2000	100	2000	100	\N
b2e130ccddc14f5b87d7d0ba745ac17d	eb0e959fe75d4084a5df98d17f7ffdcf	c484ef2fae214937865e1dbecc3bba0f			n	1970-01-01 00:00:00	-2000	100	-2000	100	\N
cb204bfa104642abb780db12c8358320	42a6cf57cd004b62a66f1190bd34eb75	90678c06f60b44caa83ac70c2b49a0c0			n	1970-01-01 00:00:00	30000	100	25000	100	\N
43c7e61d4d2a4cb49af1cb41e78a0f5f	42a6cf57cd004b62a66f1190bd34eb75	25d4d674e04b49a798ab2962e86450b5			n	1970-01-01 00:00:00	30000	100	30000	100	\N
d14ab926d23d4af984d125a1ddcb86fe	42a6cf57cd004b62a66f1190bd34eb75	c484ef2fae214937865e1dbecc3bba0f			n	1970-01-01 00:00:00	-30000	100	-30000	100	\N
8488405581ca4aa5bdb5d1cd61c34d76	42a6cf57cd004b62a66f1190bd34eb75	b6cee3641d1740e3925ffeb28ac37637			n	1970-01-01 00:00:00	-30000	100	-25000	100	\N
3d06a3c177174b8aa0517b605726918a	c8bf40fdb201416a96af8b22b94f7a2f	c484ef2fae214937865e1dbecc3bba0f			n	1970-01-01 00:00:00	32500	100	32500	100	\N
9fed84fc172444dd8f63fb285abeabde	c8bf40fdb201416a96af8b22b94f7a2f	b6cee3641d1740e3925ffeb28ac37637			n	1970-01-01 00:00:00	32500	100	25000	100	\N
b63dad2be2a74dda95f000848105a9ba	c8bf40fdb201416a96af8b22b94f7a2f	90678c06f60b44caa83ac70c2b49a0c0			n	1970-01-01 00:00:00	-32500	100	-25000	100	\N
aab20f3027db4697bfe20cf19e3a5393	c8bf40fdb201416a96af8b22b94f7a2f	25d4d674e04b49a798ab2962e86450b5			n	1970-01-01 00:00:00	-32500	100	-32500	100	\N
805104f57e484e6eae8ffb2e8ca452ad	238d64e1d17c4be08cff427b8c1139a2	2be53f0031e0408cafd491c44cdefac5			n	1970-01-01 00:00:00	20000	100	20000	100	\N
9cd4a61be04642db87a58e555c5ac337	238d64e1d17c4be08cff427b8c1139a2	c484ef2fae214937865e1dbecc3bba0f			n	1970-01-01 00:00:00	-20000	100	-20000	100	\N
\.


--
-- Data for Name: taxtable_entries; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.taxtable_entries (id, taxtable, account, amount_num, amount_denom, type) FROM stdin;
\.


--
-- Data for Name: taxtables; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.taxtables (guid, name, refcount, invisible, parent) FROM stdin;
\.


--
-- Data for Name: transactions; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.transactions (guid, currency_guid, num, post_date, enter_date, description) FROM stdin;
4a59d6b5fdfe499f946b45cc70df5d07	7c8bcccddbb24b44a3418ca6dbf87106		2026-11-01 10:59:00	2026-11-04 10:28:56	Opening Balance
bd4c936ae9124d76a2fdcebf171fec15	7c8bcccddbb24b44a3418ca6dbf87106		2026-11-02 10:59:00	2026-11-04 10:29:45	[Tag 1] [Tag 2]  Rewe
744c38b61f404779bef319baf12f8d6a	7c8bcccddbb24b44a3418ca6dbf87106		2026-11-02 10:59:00	2026-11-04 10:29:59	[Tag 1] Rewe again
bb5db0bc2b7b4dcfb357ecebafc81b0a	7c8bcccddbb24b44a3418ca6dbf87106		2026-11-03 10:59:00	2026-11-04 10:32:40	Salary
eb0e959fe75d4084a5df98d17f7ffdcf	7c8bcccddbb24b44a3418ca6dbf87106		2026-11-03 10:59:00	2026-11-04 10:33:49	Storage room
42a6cf57cd004b62a66f1190bd34eb75	7c8bcccddbb24b44a3418ca6dbf87106		2026-11-03 10:59:00	2026-11-04 10:50:00	Buy USD @ 1.2
c8bf40fdb201416a96af8b22b94f7a2f	7c8bcccddbb24b44a3418ca6dbf87106		2026-11-04 10:59:00	2026-11-04 10:50:49	Sell USD @ 1.3
238d64e1d17c4be08cff427b8c1139a2	7c8bcccddbb24b44a3418ca6dbf87106		2026-11-10 10:59:00	2026-11-04 10:35:38	Concert tickets (maybe?)
\.


--
-- Data for Name: vendors; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.vendors (guid, name, id, notes, currency, active, tax_override, addr_name, addr_addr1, addr_addr2, addr_addr3, addr_addr4, addr_phone, addr_fax, addr_email, terms, tax_inc, tax_table) FROM stdin;
\.


--
-- Data for Name: versions; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.versions (table_name, table_version) FROM stdin;
Gnucash	5000016
Gnucash-Resave	19920
books	1
commodities	1
accounts	1
budgets	1
budget_amounts	1
prices	3
transactions	4
splits	5
slots	4
recurrences	2
schedxactions	1
lots	2
billterms	2
customers	2
employees	2
entries	4
invoices	4
jobs	1
orders	1
taxtables	2
taxtable_entries	3
vendors	1
\.


--
-- Name: budget_amounts_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.budget_amounts_id_seq', 1, false);


--
-- Name: recurrences_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.recurrences_id_seq', 1, false);


--
-- Name: slots_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.slots_id_seq', 75, true);


--
-- Name: taxtable_entries_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.taxtable_entries_id_seq', 1, false);


--
-- Name: accounts accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.accounts
    ADD CONSTRAINT accounts_pkey PRIMARY KEY (guid);


--
-- Name: billterms billterms_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.billterms
    ADD CONSTRAINT billterms_pkey PRIMARY KEY (guid);


--
-- Name: books books_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.books
    ADD CONSTRAINT books_pkey PRIMARY KEY (guid);


--
-- Name: budget_amounts budget_amounts_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.budget_amounts
    ADD CONSTRAINT budget_amounts_pkey PRIMARY KEY (id);


--
-- Name: budgets budgets_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.budgets
    ADD CONSTRAINT budgets_pkey PRIMARY KEY (guid);


--
-- Name: commodities commodities_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.commodities
    ADD CONSTRAINT commodities_pkey PRIMARY KEY (guid);


--
-- Name: customers customers_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT customers_pkey PRIMARY KEY (guid);


--
-- Name: employees employees_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employees
    ADD CONSTRAINT employees_pkey PRIMARY KEY (guid);


--
-- Name: entries entries_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.entries
    ADD CONSTRAINT entries_pkey PRIMARY KEY (guid);


--
-- Name: invoices invoices_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_pkey PRIMARY KEY (guid);


--
-- Name: jobs jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.jobs
    ADD CONSTRAINT jobs_pkey PRIMARY KEY (guid);


--
-- Name: lots lots_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.lots
    ADD CONSTRAINT lots_pkey PRIMARY KEY (guid);


--
-- Name: orders orders_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (guid);


--
-- Name: prices prices_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.prices
    ADD CONSTRAINT prices_pkey PRIMARY KEY (guid);


--
-- Name: recurrences recurrences_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.recurrences
    ADD CONSTRAINT recurrences_pkey PRIMARY KEY (id);


--
-- Name: schedxactions schedxactions_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.schedxactions
    ADD CONSTRAINT schedxactions_pkey PRIMARY KEY (guid);


--
-- Name: slots slots_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.slots
    ADD CONSTRAINT slots_pkey PRIMARY KEY (id);


--
-- Name: splits splits_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.splits
    ADD CONSTRAINT splits_pkey PRIMARY KEY (guid);


--
-- Name: taxtable_entries taxtable_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.taxtable_entries
    ADD CONSTRAINT taxtable_entries_pkey PRIMARY KEY (id);


--
-- Name: taxtables taxtables_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.taxtables
    ADD CONSTRAINT taxtables_pkey PRIMARY KEY (guid);


--
-- Name: transactions transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transactions
    ADD CONSTRAINT transactions_pkey PRIMARY KEY (guid);


--
-- Name: vendors vendors_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.vendors
    ADD CONSTRAINT vendors_pkey PRIMARY KEY (guid);


--
-- Name: versions versions_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.versions
    ADD CONSTRAINT versions_pkey PRIMARY KEY (table_name);


--
-- Name: slots_guid_index; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX slots_guid_index ON public.slots USING btree (obj_guid);


--
-- Name: splits_account_guid_index; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX splits_account_guid_index ON public.splits USING btree (account_guid);


--
-- Name: splits_tx_guid_index; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX splits_tx_guid_index ON public.splits USING btree (tx_guid);


--
-- Name: tx_post_date_index; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX tx_post_date_index ON public.transactions USING btree (post_date);


--
-- PostgreSQL database dump complete
--

\unrestrict 0yW0jt9L3kzXlyhlcapavBwLvyUKILhMGarovrfsPGggooCxC1BeOu6SL2PNFZt

