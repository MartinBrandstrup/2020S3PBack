package facades;

import dtos.CountryBasicInDTO;
import dtos.CountryExDTO;
import dtos.CountryInDTO;
import dtos.CovidExDTO;
import entities.CountryData;
import entities.CovidData;
import errorhandling.DatabaseException;
import errorhandling.NotFoundException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.TypedQuery;

/**
 *
 * @author Brandstrup & Christian
 */
public class CountryFacade
{

    private static CountryFacade instance;
    private static EntityManagerFactory emf;

    //Private Constructor to ensure Singleton
    private CountryFacade()
    {
    }

    /**
     * This facade contains the following methods in order:
     *
     * getInternalCountryCount
     *
     * getInternalCountryByCode
     *
     * getAllInternalCountries
     *
     * getNewestInternalCovidEntryForCountryByCode
     *
     * getAllInternalCovidEntriesForCountryByCode
     *
     * getMultipleInternalCovidEntriesByCountryByDays
     *
     * persistAllExternalCountries
     *
     * persistExternalCountry
     *
     * persistAllExternalCovidEntriesForCountryByCode
     */
    /**
     *
     * @param _emf
     * @return an instance of this facade class.
     */
    public static CountryFacade getCountryFacade(EntityManagerFactory _emf)
    {
        if (instance == null)
        {
            emf = _emf;
            instance = new CountryFacade();
        }
        return instance;
    }

    private EntityManager getEntityManager()
    {
        return emf.createEntityManager();
    }

    /**
     * Counts the amount of entries existing in the database.
     *
     * author Brandstrup
     *
     * @return The amount of existing entries in the database.
     */
    public long getInternalCountryCount()
    {
        EntityManager em = emf.createEntityManager();
        try
        {
            long countryCount = (long) em.createQuery("SELECT COUNT(o) FROM CountryData o").getSingleResult();
            return countryCount;
        }
        finally
        {
            em.close();
        }
    }

    /**
     * Retrieves an entry from the database as a DTO object.
     *
     * author Christian
     *
     * @param code
     * @return
     * @throws NotFoundException
     */
    public CountryData getInternalCountryByCode(String code) throws NotFoundException
    {
        EntityManager em = emf.createEntityManager();
        try
        {
            CountryData country;
            TypedQuery<CountryData> query = em.createQuery("SELECT o FROM CountryData o "
                    + "WHERE o.countryCode = :code", CountryData.class)
                    .setParameter("code", code);
            country = query.getSingleResult();

            if (country == null)
            {
                throw new NotFoundException("No object matching provided country code exists in database.");
            }

            return country;
        }
        finally
        {
            em.close();
        }
    }

    /**
     * Retrieves all entries from the database as DTO objects.
     *
     * author Brandstrup
     *
     * @return a List of DTO objects.
     */
    public List<CountryBasicInDTO> getAllInternalCountries() throws NotFoundException
    {
        EntityManager em = getEntityManager();
        try
        {
            List<CountryBasicInDTO> countryBasicDTOList = new ArrayList<>();
            TypedQuery<CountryData> query
                    = em.createQuery("SELECT o FROM CountryData o", CountryData.class);

            if (query.getResultList() == null)
            {
                throw new NotFoundException("No objects retrieved from database.");
            }

            if (query.getResultList().isEmpty())
            {
                throw new NotFoundException("Database is empty.");
            }

            query.getResultList().forEach((o) ->
            {
                countryBasicDTOList.add(new CountryBasicInDTO(o));
            });

            return countryBasicDTOList;
        }
        finally
        {
            em.close();
        }
    }

    /**
     *
     * author Brandstrup
     *
     * @param code the alpha2code of the country you are attempting to retrieve 
     * from the database.
     * @return null if no Country exists with provided code; the Country data 
     * only if no Covid data exists for country; else a combined Country and 
     * Covid DTO.
     */
    public CountryInDTO getNewestInternalCovidEntryForCountryByCode(String code)
    {
        EntityManager em = getEntityManager();
        try
        {
            CountryInDTO result = null;
            CountryData country = null;
            
            TypedQuery<CountryData> query = em.createQuery("SELECT country FROM CountryData country "
                    + "WHERE country.countryCode = :code", CountryData.class)
                    .setParameter("code", code);
            country = query.getSingleResult();

            if (country == null || country.getCountryCode().length() < 2)
            {
//                throw new NotFoundException("No object matching provided id exists in database.");
                return result;
            }
            else if (!(country.getCovidEntries().isEmpty()))
            {
                // finds the highest value in a HashSet by date and returns it
                HashSet<CovidData> covidEntries = new HashSet<>(country.getCovidEntries());
                CovidData newestEntry = Collections.max(covidEntries, (CovidData o1, CovidData o2) ->
                {
                    return o1.getDate().compareTo(o2.getDate());
                });
                long newestCovidId = newestEntry.getId();

                CovidData covid = em.find(CovidData.class, newestCovidId);
                result = new CountryInDTO(country, covid);
                return result;
            }
            else
            {
                result = new CountryInDTO(country);
                return result;
            }
        }
        finally
        {
            em.close();
        }
    }

    /**
     * author Brandstrup
     *
     */
    public void getAllInternalCovidEntriesForCountryByCode()
    {
        throw new UnsupportedOperationException();
    }

    /**
     *
     * author Brandstrup
     *
     * @param code the alpha2code of the country you are attempting to retrieve 
     * from the database.
     * @param days the number of days you want to retrieve since today.
     * @return null if no Country exists with provided code; the Country data 
     * only if no Covid data exists for country; else a List of combined Country 
     * and Covid DTOs.
     */
    public List<CountryInDTO> getMultipleInternalCovidEntriesByCountryByDays(String code, int days)
    {
        EntityManager em = getEntityManager();
        try
        {
            List<CountryInDTO> result = null;
            CountryData country = null;
            List<CovidData> covidList = null;
            
            TypedQuery<CountryData> countryQuery 
                    = em.createQuery("SELECT country FROM CountryData country "
                    + "WHERE country.countryCode = :code", CountryData.class)
                    .setParameter("code", code);
            country = countryQuery.getSingleResult();

            if (country == null || country.getCountryCode().length() < 2)
            {
//                throw new NotFoundException("No object matching provided id exists in database.");
                return result;
            }
            else if (!(country.getCovidEntries().isEmpty()))
            {
                // sorts an ArrayList by date and returns the most recent entries
                List<CovidData> covidEntries = new ArrayList<>(country.getCovidEntries());
                covidEntries.sort((CovidData o1, CovidData o2) ->
                {
                    return o1.getDate().compareTo(o2.getDate());
                });
                
                // creates a List<String> containing the ids of the entries we 
                // want to retrieve from the database.
                
                String[] idStrings = new String[days];
                for (int i = 0; i < days; i++)
                {
                    idStrings[i] = Long.toString(covidEntries.get(i).getId());
                }
                List<String> covidIds = Arrays.asList(idStrings);
                
                TypedQuery<CovidData> covidQuery
                    = em.createQuery("SELECT covid FROM CovidData covid "
                            + "WHERE covid.id IN (:ids)", CovidData.class)
                        .setParameter("ids", covidIds);
                covidList = covidQuery.getResultList();
                
                for (CovidData covid : covidList)
                {
                    result.add(new CountryInDTO(country, covid));
                }
            
                return result;
            }
            else
            {
                for (int i = 0; i < days; i++)
                {
                    result.add(new CountryInDTO(country));
                }
                return result;
            }
        }
        finally
        {
            em.close();
        }
    }

    /**
     * author Christian
     *
     * @param DTOList
     * @throws NotFoundException
     * @throws DatabaseException
     */
    public void persistAllExternalCountries(List<CountryExDTO> DTOList) throws NotFoundException, DatabaseException
    {
        if (DTOList.isEmpty())
        {
            throw new NotFoundException("No objects retrieved from http://restcountries.eu/rest/v1.");
        }

        HashMap existingCountryMap = new HashMap<String, String>();
        EntityManager em = emf.createEntityManager();
        try
        {
            em.getTransaction().begin();

            TypedQuery<CountryData> query
                    = em.createQuery("SELECT o FROM CountryData o", CountryData.class);

            if (query.getResultList() == null)
            {
                throw new DatabaseException("No objects retrieved from database.");
            }

            query.getResultList().forEach((o) ->
            {
                existingCountryMap.put(o.getCountryCode().toLowerCase(), o.getCountryName());
            });

            for (CountryExDTO newCountry : DTOList)
            {
                String newCountryCode = newCountry.getAlpha2Code().toLowerCase();
                
                if (existingCountryMap.get(newCountryCode) == null)
//                if (!(existingCountryMap.containsKey(newCountryCode)))
                {
                    CountryData cd = new CountryData(newCountry.getName(), newCountry.getAlpha2Code(), newCountry.getPopulation(), null, null);
                    em.persist(cd);
                    System.out.println("Entry with name --> " + newCountry.getName() + " <-- has been persisted");
                }
            }
            em.getTransaction().commit();
        }
        finally
        {
            em.close();
        }
    }

    /**
     * author Christian
     *
     * @param DTO
     * @return
     * @throws NotFoundException
     * @throws DatabaseException if an identical object already exists in the
     * database
     */
    public CountryData persistExternalCountry(CountryExDTO DTO) throws NotFoundException, DatabaseException
    {
        // this guard makes no sense. There will always be a DTO otherwise you won't be able to call the method
        if (DTO == null)
        {
            throw new NotFoundException("No objects passed");
        }

        CountryData cd;
        EntityManager em = emf.createEntityManager();
        try
        {
            em.getTransaction().begin();
            cd = new CountryData(DTO.getName(), DTO.getAlpha2Code(), DTO.getPopulation(), null, null);
            em.persist(cd);
            em.getTransaction().commit();
            return cd;
        }
        catch (EntityExistsException ex)
        {
            throw new DatabaseException("An identical object entry already exists in the database.");
        }
        finally
        {
            em.close();
        }
        //http://restcountries.eu/rest/v1/alpha?codes=de
    }

    /**
     * author Brandstrup
     *
     * @param exDTOList
     * @param code
     * @throws NotFoundException
     */
    public void persistAllExternalCovidEntriesForCountryByCode(List<CovidExDTO> exDTOList, String code) throws NotFoundException
    {
        if (exDTOList.isEmpty())
        {
            throw new NotFoundException("No objects retrieved from https://api.covid19api.com/total/dayone/country/" + code + ".");
        }

        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH);
        List<CovidExDTO> filteredDTOList = new ArrayList<>();
        CountryData country;
        HashMap existingDates = new HashMap<String, String>();

        for (CovidExDTO covidExDTO : exDTOList)
        {
            if (covidExDTO.getProvince().isEmpty())
            {
                filteredDTOList.add(covidExDTO);
            }
        }

        EntityManager em = emf.createEntityManager();
        try
        {
            em.getTransaction().begin();

            TypedQuery<CountryData> query = em.createQuery("SELECT o FROM CountryData o "
                    + "WHERE o.countryCode = :code", CountryData.class)
                    .setParameter("code", code);
            country = query.getSingleResult();

            if (country == null)
            {
                throw new NotFoundException("No object matching provided country code exists in database.");
            }

            if (!(country.getCovidEntries() == null || country.getCovidEntries().isEmpty()))
            {
                for (CovidData covid : country.getCovidEntries())
                {
                    String existingDate = covid.getLocalDate().toLocalDate().toString();
                    existingDates.put(existingDate, null);
                }
            }

            for (CovidExDTO o : filteredDTOList)
            {
                LocalDate newDate = LocalDate.parse(o.getDate(), inputFormatter);
//                DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH);
//                String formattedDate = outputFormatter.format(localDate);
//                System.out.println(formattedDate);
                if (existingDates.get(newDate.toString()) == null)
//                if (!(existingDates.containsKey(newDate.toString())))
                {
                    Date date = Date.from(newDate.atStartOfDay(ZoneId.systemDefault()).toInstant().plusSeconds(86400));
                    long newConfirmed = 0;
                    long newRecovered = 0;
                    long newDeaths = 0;

                    CovidData covid = new CovidData(date, null, newConfirmed, o.getConfirmed(),
                            newRecovered, o.getRecovered(), newDeaths, o.getDeaths());
                    country.addCovidEntry(covid);
                    em.persist(covid);
                    System.out.println("Entry with date --> " + newDate.toString() + " <-- has been persisted");
                }
            }
            em.merge(country);
            em.getTransaction().commit();
        }
        finally
        {
            em.close();
        }
        //https://api.covid19api.com/total/dayone/country/de
    }

}
